package com.aistudio.sanctuary.audpbk

import com.aistudio.sanctuary.audpbk.ui.viewmodel.AudiobookViewModel

object PlaybackController {
    var activeViewModel: AudiobookViewModel? = null

    fun play() {
        activeViewModel?.let { vm ->
            if (!vm.isPlaying.value) {
                vm.togglePlayPause()
            }
        }
    }

    fun pause() {
        activeViewModel?.let { vm ->
            if (vm.isPlaying.value) {
                vm.togglePlayPause()
            }
        }
    }
}
