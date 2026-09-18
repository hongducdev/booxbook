package com.booxbook.core.engine.epub

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the most recently created Readium [FragmentFactory].
 *
 * `EpubNavigatorFragment` has no no-arg constructor, so the hosting
 * [androidx.fragment.app.FragmentManager] can only re-create it (configuration change,
 * process death) through the Readium factory. The factory is only available once a book
 * has been opened, therefore it is parked here and consumed by
 * [RestorableReadiumFragmentFactory].
 */
@Singleton
class ReadiumFragmentFactoryProvider @Inject constructor() {

    @Volatile
    var factory: FragmentFactory? = null
}

/**
 * Delegates fragment instantiation to the latest Readium factory when available and falls
 * back to the default no-arg constructor lookup otherwise.
 *
 * Install it on the Activity's FragmentManager *before* `super.onCreate()` so restored
 * navigator fragments can be re-instantiated.
 */
@Singleton
class RestorableReadiumFragmentFactory @Inject constructor(
    private val provider: ReadiumFragmentFactoryProvider
) : FragmentFactory() {

    /**
     * True when a publication is currently open, i.e. a restored navigator fragment can
     * actually be re-instantiated.
     */
    val canRestoreNavigator: Boolean
        get() = provider.factory != null

    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        val delegate = provider.factory
        if (delegate != null) {
            try {
                return delegate.instantiate(classLoader, className)
            } catch (_: Throwable) {
                // Not a Readium fragment (or the publication is gone): use the default path.
            }
        }
        return super.instantiate(classLoader, className)
    }
}
