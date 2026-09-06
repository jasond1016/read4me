package com.read4me.app.data

import java.io.File

/** Missing files may be retained only when an existing manifest already references them. */
internal fun canRetainMediaFile(file: File, previouslyPublished: Set<File>): Boolean =
    file.isFile || (!file.exists() && file.canonicalFile in previouslyPublished)
