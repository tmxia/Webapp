package com.webtoapp.ui.shared







internal data class GalleryAdvance(
    val targetIndex: Int,
    
    val reshuffle: Boolean
)




internal fun galleryAutoAdvanceTarget(
    currentIndex: Int,
    itemCount: Int,
    loop: Boolean,
    shuffleOnLoop: Boolean
): GalleryAdvance? {
    if (itemCount <= 0) return null
    if (currentIndex < itemCount - 1) return GalleryAdvance(currentIndex + 1, reshuffle = false)
    if (!loop) return null
    return GalleryAdvance(targetIndex = 0, reshuffle = shuffleOnLoop)
}
