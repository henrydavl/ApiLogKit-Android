package com.henrydavl.apilogkit.util

import androidx.core.content.FileProvider
import com.henrydavl.apilogkit.R

/**
 * ApiLogKit's own [FileProvider], used to hand exported logs to the share sheet.
 *
 * It exists as a named subclass rather than a plain `androidx.core.content.FileProvider`
 * entry in the manifest because the manifest merger keys providers by
 * `android:name`: declaring the base class here would collide with any host app
 * that already uses a FileProvider of its own, and fail their build. Subclassing
 * gives this provider a distinct name so the two coexist.
 *
 * The paths resource is supplied through the constructor, which also removes the
 * need for a `FILE_PROVIDER_PATHS` meta-data element.
 */
internal class ApiLogFileProvider : FileProvider(R.xml.apilogkit_file_paths)
