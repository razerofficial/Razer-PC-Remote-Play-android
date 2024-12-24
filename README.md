# Razer PC Remote Play for Android

[Razer PC Remote Play for Android](https://www.razer.com/remote-play-mobile-client) is an open source client for Razer PC Remote Play and [Razer Cortex](https://www.razer.com/cortex).

THE ULTIMATE PC-TO-MOBILE STREAMING PLATFORM
The power of your gaming rig now fits in your pocket. Stream your favorite games using your PC, launch them directly from your mobile device, and take your immersion to the next level with the sharpest, smoothest visuals.
 
Remote Play also has a [PC client](https://www.razer.com/cortex)

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.razer.neuron)

## Building
* Install Android Studio and the Android NDK
* In Razer-PC-Remote-Play-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors
* [???](???)  

Razer PC Remote Play is derived from moonlight, an open-source project licensed under the GNU General Public License v3. 
This project includes modified versions of the files in
* app/src/neuron/*
* app/src/debug/*
* app/build.gradle
* build.gradle


Moonlight for Android is available at [moonlight-android](https://github.com/moonlight-stream/moonlight-android) and Moonlight Common c is available at [moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c)