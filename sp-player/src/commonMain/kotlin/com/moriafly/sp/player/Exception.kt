package com.moriafly.sp.player

@UnstableSpPlayerApi
abstract class SaltPlayerException : Exception()

@UnstableSpPlayerApi
class UnLoadedException : SaltPlayerException()

@UnstableSpPlayerApi
class UnSupportedMediaItemException : SaltPlayerException()
