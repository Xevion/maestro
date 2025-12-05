package maestro.api.command.exception

import maestro.api.AgentAPI
import maestro.api.command.Command
import maestro.api.command.argument.ICommandArgument
import maestro.api.utils.Loggers
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.Logger

/**
 * The base for a Maestro Command Exception, checked or unchecked. Provides a [handle]
 * method that is used to provide useful output to the user for diagnosing issues that
 * may have occurred during execution.
 *
 * Anything implementing this interface should be assignable to [Exception].
 */
interface ICommandException {
    /**
     * @return The exception details
     * @see Exception.message
     */
    val message: String?

    /**
     * Called when this exception is thrown, to handle the exception.
     *
     * @param command The command that threw it.
     * @param args The arguments the command was called with.
     */
    fun handle(
        command: Command?,
        args: List<ICommandArgument>?,
    ) {
        // Log error
        log.atError().log(this.message)

        // Send to chat directly (bypasses appender to ensure it shows)
        val errorMsg = Component.literal("[cmd] ")
        errorMsg.style = errorMsg.style.withColor(ChatFormatting.AQUA)

        val messageComponent = Component.literal(this.message ?: "Unknown error")
        messageComponent.style = messageComponent.style.withColor(ChatFormatting.RED)
        errorMsg.append(messageComponent)

        Minecraft.getInstance().execute {
            AgentAPI
                .getSettings()
                .logger.value
                .accept(errorMsg)
        }
    }

    companion object {
        @JvmField
        val log: Logger = Loggers.get("cmd")
    }
}
