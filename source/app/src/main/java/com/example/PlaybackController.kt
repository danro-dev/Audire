package com.example

import com.example.ui.viewmodel.AudiobookViewModel

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
