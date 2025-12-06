package maestro.command.exception

import maestro.Agent
import maestro.command.ICommand
import maestro.command.argument.ICommandArgument
import maestro.utils.Loggers
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.Logger

/**
 * Sealed hierarchy for all Maestro command exceptions.
 * Provides type-safe error handling with exhaustive when expressions.
 */
sealed class CommandException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause),
    ICommandException {
    // Simple exceptions

    data class NotFound(
        val command: String,
    ) : CommandException(
            "Command not found: $command",
        ) {
        fun handle(
            command: ICommand?,
            args: List<ICommandArgument>?,
        ) {
            // Log as warning
            log.atWarn().addKeyValue("command", this.command).log("Command not found")

            // Send to chat
            val errorMsg = Component.literal("[cmd] ")
            errorMsg.style = errorMsg.style.withColor(ChatFormatting.AQUA)

            val messageComponent = Component.literal(message ?: "Unknown error")
            messageComponent.style = messageComponent.style.withColor(ChatFormatting.GRAY)
            errorMsg.append(messageComponent)

            Minecraft.getInstance().execute {
                Agent
                    .getPrimaryAgent()
                    .getSettings()
                    .logger.value
                    .accept(errorMsg)
            }
        }

        companion object {
            private val log: Logger = Loggers.Cmd.get()
        }
    }

    data class InvalidState(
        val reason: String,
    ) : CommandException(reason)

    data class NotEnoughArguments(
        val minArgs: Int,
    ) : CommandException(
            "Not enough arguments (expected at least $minArgs)",
        )

    data class TooManyArguments(
        val maxArgs: Int,
    ) : CommandException(
            "Too many arguments (expected at most $maxArgs)",
        )

    open class ErrorMessage(
        message: String,
    ) : CommandException(message)

    // Nested sealed class for argument errors

    sealed class InvalidArgument(
        val arg: ICommandArgument,
        message: String,
        cause: Throwable? = null,
    ) : CommandException(formatMessage(arg, message), cause) {
        class InvalidType : InvalidArgument {
            constructor(
                arg: ICommandArgument,
                expected: String,
            ) : super(arg, "Expected $expected")

            constructor(
                arg: ICommandArgument,
                expected: String,
                cause: Throwable,
            ) : super(arg, "Expected $expected", cause)

            constructor(
                arg: ICommandArgument,
                expected: String,
                got: String,
            ) : super(arg, "Expected $expected, but got $got instead")

            constructor(
                arg: ICommandArgument,
                expected: String,
                got: String,
                cause: Throwable,
            ) : super(arg, "Expected $expected, but got $got instead", cause)
        }

        companion object {
            private fun formatMessage(
                arg: ICommandArgument,
                message: String,
            ): String {
                val argIndex = if (arg.index == -1) "<unknown>" else (arg.index + 1).toString()
                return "Error at argument #$argIndex: $message"
            }
        }
    }

    // Special cases with custom behavior
    open class Unhandled :
        RuntimeException,
        ICommandException {
        constructor(message: String?) : super(message)

        constructor(cause: Throwable?) : super(cause)

        fun handle(
            command: ICommand?,
            args: List<ICommandArgument>?,
        ) {
            // Log error with exception
            log.atError().setCause(this).log("Unhandled exception occurred")

            // Send to chat
            val errorMsg = Component.literal("[cmd] ")
            errorMsg.style = errorMsg.style.withColor(ChatFormatting.AQUA)

            val messageComponent =
                Component.literal(
                    "An unhandled exception occurred. The error is in your game's log, please " +
                        "report this at https://github.com/cabaletta/baritone/issues",
                )
            messageComponent.style = messageComponent.style.withColor(ChatFormatting.RED)
            errorMsg.append(messageComponent)

            Minecraft.getInstance().execute {
                Agent
                    .getPrimaryAgent()
                    .getSettings()
                    .logger.value
                    .accept(errorMsg)
            }

            // Print stack trace to console
            printStackTrace()
        }

        companion object {
            private val log: Logger = Loggers.Cmd.get()
        }
    }

    class NoParserForType(
        klass: Class<*>,
    ) : Unhandled(
            "Could not find a handler for type ${klass.simpleName}",
        )
}
