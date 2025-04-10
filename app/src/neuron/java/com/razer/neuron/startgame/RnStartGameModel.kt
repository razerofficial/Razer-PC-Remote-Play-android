package com.razer.neuron.startgame

import android.content.Intent
import com.limelight.nvstream.http.ComputerDetails


class RnStartGameModel {

    sealed class Navigation(val id: String) {

        class Stream(val gameIntent: Intent) : Navigation("stream")

        /**
         * Only for cases when existing game is the same game as [startGameName]
         */
        class StartSameGameOrQuit(
            val startGameName: String,
            val computerDetails: ComputerDetails,
            val gameIntent: Intent
        ) : Navigation("start_or_quit")

        /**
         * Only for cases when existing game is the same game as [startGameName]
         * but different devices
         */
        class ReplaceSessionOrQuit(
            val runningGameDeviceNickName: String,
            val startGameName: String,
            val computerDetails: ComputerDetails,
            val gameIntent: Intent
        ) : Navigation("start_or_quit")

        /**
         * Only for cases when existing game is not the same game as [startGameName]
         */
        class ConfirmQuitThenStartDifferentGame(
            val runningGameName: String?,
            val startGameName: String,
            val computerDetails: ComputerDetails,
            val gameIntent: Intent
        ) : Navigation("confirm-quit")

        class Error(val throwable: Throwable) : Navigation("error")

        object Finish : Navigation("finish")
    }

    sealed class State(val id: String) {
        object Empty : State("empty")

        object ShowLoading : State("show_loading")

        object HideLoading : State("hide_loading")
    }

}
