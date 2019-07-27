package me.kenzierocks.mcpide.util

import mu.KLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

private fun determineLoggerLevel(logger: KLogger): Level {
    return when {
        logger.isDebugEnabled -> Level.DEBUG
        logger.isInfoEnabled -> Level.INFO
        else -> Level.ERROR
    }
}

class KLogLogger(
    private val logger: KLogger
) : Logger(determineLoggerLevel(logger)) {
    override fun log(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.ERROR -> logger.error(msg)
        }
    }
}
