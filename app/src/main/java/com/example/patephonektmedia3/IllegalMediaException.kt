package com.example.patephonektmedia3

class IllegalMediaException(fileName: String?, e: Exception, message: String = "File not supported: $fileName; $e") : Exception(message)