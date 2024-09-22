package me.pog5.commanddumper.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import kotlinx.io.IOException
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.Text
import net.minecraft.text.Text.literal
import net.minecraft.util.Formatting
import java.io.File


class CommanddumperClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher: CommandDispatcher<FabricClientCommandSource?>, registryAccess: CommandRegistryAccess? ->
            dispatcher.register(
                ClientCommandManager.literal("dumpcommands").executes { context: CommandContext<FabricClientCommandSource> ->
                    context.source.sendFeedback(literal("Dumping commands...").formatted(Formatting.GRAY))
                    dumpCommands(context.source)
                    context.source.sendFeedback(literal("Done!").formatted(Formatting.GREEN))
                    0
                }
            )
        })
    }

    private fun dumpCommands(source: FabricClientCommandSource) {
        val client = MinecraftClient.getInstance()
        val player = client.player
        val networkHandler = client.networkHandler

        if (networkHandler == null || player == null) {
            source.sendError(Text.of("Not connected to a server"))
            return
        }

        // Get server address (use "local" if in single-player)
        val serverAddress = client.currentServerEntry?.address ?: "local"

        // Get the command dispatcher containing all available commands
        val commandDispatcher = networkHandler.commandDispatcher

        // Collect all commands recursively
        val commands = mutableListOf<String>()
        collectCommands(commandDispatcher.root, "", commands)

        // Sort the commands alphabetically
        commands.sort()

        // Ensure the dumpedcommands directory exists
        val dumpedCommandsDir = File(client.runDirectory, "dumpedcommands")
        if (!dumpedCommandsDir.exists()) {
            dumpedCommandsDir.mkdirs()
        }

        // Prepare the output file with the server IP as the filename
        val fileName = serverAddress.replace(":", "_") + ".txt"
        val file = File(dumpedCommandsDir, fileName)

        try {
            // Write all commands to the file
            file.writeText(commands.joinToString("\n"))
            source.sendFeedback(Text.of("Commands dumped to ${file.absolutePath}"))
        } catch (e: IOException) {
            source.sendError(Text.of("Failed to write commands to file: ${e.message}"))
        }
    }

    private fun collectCommands(
        node: CommandNode<*>,
        prefix: String,
        commands: MutableList<String>
    ) {
        var newPrefix = prefix
        if (node is LiteralCommandNode<*>) {
            newPrefix = if (prefix.isEmpty()) node.name else "$prefix ${node.name}"
            commands.add(newPrefix)
        }

        // Recursively collect child commands
        node.children.forEach { child ->
            collectCommands(child, newPrefix, commands)
        }
    }
}
