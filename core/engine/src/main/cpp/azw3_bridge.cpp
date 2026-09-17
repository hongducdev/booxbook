#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstring>
#include <android/log.h>

extern "C" {
#include "mobi.h"
#include "util.h"
#include "opf.h"
}

// Include miniz zip archive writing definitions
#define MINIZ_HEADER_FILE_ONLY
#include "miniz.c"

#define TAG "Azw3Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define EPUB_CONTAINER "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n\
  <rootfiles>\n\
    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n\
  </rootfiles>\n\
</container>"

#define EPUB_MIMETYPE "application/epub+zip"

enum Azw3ResultCode {
    AZW3_SUCCESS = 0,
    AZW3_ERROR_DRM = 1,
    AZW3_ERROR_LOAD_FAILED = 2,
    AZW3_ERROR_PARSE_FAILED = 3,
    AZW3_ERROR_ZIP_FAILED = 4,
    AZW3_ERROR_UNKNOWN = 5
};

static int write_epub_archive(const MOBIRawml *rawml, const char *output_epub_path) {
    if (rawml == nullptr) {
        return AZW3_ERROR_PARSE_FAILED;
    }

    mz_zip_archive zip;
    memset(&zip, 0, sizeof(mz_zip_archive));

    // Remove existing destination file if present
    remove(output_epub_path);

    mz_bool mz_ret = mz_zip_writer_init_file(&zip, output_epub_path, 0);
    if (!mz_ret) {
        LOGE("Could not initialize zip archive: %s", output_epub_path);
        return AZW3_ERROR_ZIP_FAILED;
    }

    // 1. mimetype (Must be first, stored with 0 compression)
    mz_ret = mz_zip_writer_add_mem(&zip, "mimetype", EPUB_MIMETYPE, sizeof(EPUB_MIMETYPE) - 1, MZ_NO_COMPRESSION);
    if (!mz_ret) {
        LOGE("Could not add mimetype to epub zip");
        mz_zip_writer_end(&zip);
        remove(output_epub_path);
        return AZW3_ERROR_ZIP_FAILED;
    }

    // 2. META-INF/container.xml
    mz_ret = mz_zip_writer_add_mem(&zip, "META-INF/container.xml", EPUB_CONTAINER, sizeof(EPUB_CONTAINER) - 1, (mz_uint)MZ_DEFAULT_COMPRESSION);
    if (!mz_ret) {
        LOGE("Could not add META-INF/container.xml to epub zip");
        mz_zip_writer_end(&zip);
        remove(output_epub_path);
        return AZW3_ERROR_ZIP_FAILED;
    }

    char partname[512];

    // 3. Main text files: rawml->markup
    if (rawml->markup != nullptr) {
        MOBIPart *curr = rawml->markup;
        while (curr != nullptr) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "OEBPS/part%05zu.%s", curr->uid, file_meta.extension);
            mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint)MZ_DEFAULT_COMPRESSION);
            if (!mz_ret) {
                LOGE("Could not add markup part: %s", partname);
                mz_zip_writer_end(&zip);
                remove(output_epub_path);
                return AZW3_ERROR_ZIP_FAILED;
            }
            curr = curr->next;
        }
    }

    // 4. Supplementary flow files: rawml->flow
    if (rawml->flow != nullptr) {
        MOBIPart *curr = rawml->flow;
        // Skip raw html file (first node)
        if (curr != nullptr) {
            curr = curr->next;
        }
        while (curr != nullptr) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "OEBPS/flow%05zu.%s", curr->uid, file_meta.extension);
            mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint)MZ_DEFAULT_COMPRESSION);
            if (!mz_ret) {
                LOGE("Could not add flow part: %s", partname);
                mz_zip_writer_end(&zip);
                remove(output_epub_path);
                return AZW3_ERROR_ZIP_FAILED;
            }
            curr = curr->next;
        }
    }

    // 5. Binary resources and OPF: rawml->resources
    if (rawml->resources != nullptr) {
        MOBIPart *curr = rawml->resources;
        while (curr != nullptr) {
            if (curr->size > 0) {
                MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
                if (file_meta.type == T_OPF) {
                    snprintf(partname, sizeof(partname), "OEBPS/content.opf");
                } else {
                    snprintf(partname, sizeof(partname), "OEBPS/resource%05zu.%s", curr->uid, file_meta.extension);
                }
                mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint)MZ_DEFAULT_COMPRESSION);
                if (!mz_ret) {
                    LOGE("Could not add resource part: %s", partname);
                    mz_zip_writer_end(&zip);
                    remove(output_epub_path);
                    return AZW3_ERROR_ZIP_FAILED;
                }
            }
            curr = curr->next;
        }
    }

    // Finalize zip archive
    mz_ret = mz_zip_writer_finalize_archive(&zip);
    if (!mz_ret) {
        LOGE("Could not finalize zip archive");
        mz_zip_writer_end(&zip);
        remove(output_epub_path);
        return AZW3_ERROR_ZIP_FAILED;
    }
    mz_zip_writer_end(&zip);

    return AZW3_SUCCESS;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_booxbook_core_engine_azw3_Azw3Converter_nativeConvertAzw3ToEpub(
        JNIEnv *env,
        jobject /* thiz */,
        jstring j_input_path,
        jstring j_output_epub_path
) {
    if (j_input_path == nullptr || j_output_epub_path == nullptr) {
        return AZW3_ERROR_LOAD_FAILED;
    }

    const char *input_path = env->GetStringUTFChars(j_input_path, nullptr);
    const char *output_epub_path = env->GetStringUTFChars(j_output_epub_path, nullptr);

    LOGI("Converting AZW3: %s -> %s", input_path, output_epub_path);

    MOBIData *m = mobi_init();
    if (m == nullptr) {
        LOGE("mobi_init failed");
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);
        return AZW3_ERROR_LOAD_FAILED;
    }

    MOBI_RET ret = mobi_load_filename(m, input_path);
    if (ret != MOBI_SUCCESS) {
        LOGE("mobi_load_filename failed with code: %d", ret);
        mobi_free(m);
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);
        return AZW3_ERROR_LOAD_FAILED;
    }

    if (mobi_is_encrypted(m)) {
        LOGI("AZW3 file has DRM protection: %s", input_path);
        mobi_free(m);
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);
        return AZW3_ERROR_DRM;
    }

    MOBIRawml *rawml = mobi_init_rawml(m);
    if (rawml == nullptr) {
        LOGE("mobi_init_rawml failed");
        mobi_free(m);
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);
        return AZW3_ERROR_PARSE_FAILED;
    }

    ret = mobi_parse_rawml(rawml, m);
    if (ret != MOBI_SUCCESS) {
        LOGE("mobi_parse_rawml failed with code: %d", ret);
        mobi_free_rawml(rawml);
        mobi_free(m);
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);
        return AZW3_ERROR_PARSE_FAILED;
    }

    int zip_result = write_epub_archive(rawml, output_epub_path);

    mobi_free_rawml(rawml);
    mobi_free(m);
    env->ReleaseStringUTFChars(j_input_path, input_path);
    env->ReleaseStringUTFChars(j_output_epub_path, output_epub_path);

    return zip_result;
}

JNIEXPORT jboolean JNICALL
Java_com_booxbook_core_engine_azw3_Azw3Converter_nativeIsDrmProtected(
        JNIEnv *env,
        jobject /* thiz */,
        jstring j_input_path
) {
    if (j_input_path == nullptr) {
        return JNI_FALSE;
    }

    const char *input_path = env->GetStringUTFChars(j_input_path, nullptr);
    MOBIData *m = mobi_init();
    if (m == nullptr) {
        env->ReleaseStringUTFChars(j_input_path, input_path);
        return JNI_FALSE;
    }

    MOBI_RET ret = mobi_load_filename(m, input_path);
    bool is_drm = false;
    if (ret == MOBI_SUCCESS) {
        is_drm = mobi_is_encrypted(m);
    }

    mobi_free(m);
    env->ReleaseStringUTFChars(j_input_path, input_path);
    return is_drm ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_booxbook_core_engine_azw3_Azw3Converter_nativeExtractCover(
        JNIEnv *env,
        jobject /* thiz */,
        jstring j_input_path,
        jstring j_output_cover_path
) {
    if (j_input_path == nullptr || j_output_cover_path == nullptr) {
        return JNI_FALSE;
    }

    const char *input_path = env->GetStringUTFChars(j_input_path, nullptr);
    const char *output_cover_path = env->GetStringUTFChars(j_output_cover_path, nullptr);

    MOBIData *m = mobi_init();
    if (m == nullptr) {
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_cover_path, output_cover_path);
        return JNI_FALSE;
    }

    MOBI_RET ret = mobi_load_filename(m, input_path);
    if (ret != MOBI_SUCCESS) {
        mobi_free(m);
        env->ReleaseStringUTFChars(j_input_path, input_path);
        env->ReleaseStringUTFChars(j_output_cover_path, output_cover_path);
        return JNI_FALSE;
    }

    MOBIPdbRecord *record = nullptr;
    MOBIExthHeader *exth = mobi_get_exthrecord_by_tag(m, EXTH_COVEROFFSET);
    if (exth != nullptr) {
        uint32_t offset = mobi_decode_exthvalue(static_cast<const unsigned char*>(exth->data), exth->size);
        size_t first_resource = mobi_get_first_resource_record(m);
        size_t uid = first_resource + offset;
        record = mobi_get_record_by_seqnumber(m, uid);
    }

    bool success = false;
    if (record != nullptr && record->size > 0 && record->data != nullptr) {
        FILE *f = fopen(output_cover_path, "wb");
        if (f != nullptr) {
            size_t written = fwrite(record->data, 1, record->size, f);
            fclose(f);
            success = (written == record->size);
        }
    }

    mobi_free(m);
    env->ReleaseStringUTFChars(j_input_path, input_path);
    env->ReleaseStringUTFChars(j_output_cover_path, output_cover_path);

    return success ? JNI_TRUE : JNI_FALSE;
}

}
