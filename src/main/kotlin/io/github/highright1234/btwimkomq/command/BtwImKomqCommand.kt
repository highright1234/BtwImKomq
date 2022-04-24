package io.github.highright1234.btwimkomq.command

import com.google.gson.JsonObject
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.applyFaking
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.unApplyFaking
import net.md_5.bungee.BungeeCord
import net.md_5.bungee.UserConnection
import net.md_5.bungee.Util
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.http.HttpClient
import java.util.*


object BtwImKomqCommand : Command("btwimkomq", "btwimkomq.command", "bik") {
    private const val NAME_TO_UUID_URL = "https://api.mojang.com/users/profiles/minecraft/%s"
    private fun UserConnection.playerUUID(name : String, future : (result: UUID?, error: Throwable?) -> Unit) {
        HttpClient.get(NAME_TO_UUID_URL.format(name), ch.handle.eventLoop()) { result0, error ->
            var uuid : UUID? = null
            result0?.let { responseData ->
                val response: JsonObject = BungeeCord.getInstance().gson.fromJson(responseData, JsonObject::class.java)
                uuid = Util.getUUID(response.get("id").asString)
            }
            future(uuid, error)
        }
    }

    @Suppress("DEPRECATION")
    private fun UserConnection.applyFaking(newName : String, sender : CommandSender = this) {
        playerUUID(newName) { result, error ->
            result?.let {
                applyFaking(result) {
                    sender.sendMessage("${ChatColor.DARK_AQUA}Successfully applied")
                }
            }
            error?.let {
                sender.sendMessage("Error occurred during processing, check your logs for more information")
                it.printStackTrace()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun execute(sender: CommandSender, args: Array<out String>) {
        when (args.size) {
            0 -> {
                sender.sendMessage("""
                    ${ChatColor.GOLD}==============================
                    ${ChatColor.YELLOW}/bik <NewName>
                    ${ChatColor.YELLOW}/bik <Player> <NewName>
                    ${ChatColor.GOLD}==============================
                """.trimIndent())
            }
            else -> {
                when (args[0].lowercase()) {
                    "apply" -> {
                        when (args.size-1) {
                            1 -> {
                                if (sender is UserConnection) {
                                    sender.applyFaking(args[1])
                                } else {
                                    sender.sendMessage("ㅗ")
                                }
                            }
                            2 -> {
                                val player = BungeeCord.getInstance().getPlayer(args[1]) as UserConnection
                                player.applyFaking(args[2], sender)
                            }
                        }
                    }
                    "unapply" -> {
                        when (args.size-1) {
                            0 -> {
                                if (sender is UserConnection) {
                                    sender.unApplyFaking()
                                } else {
                                    sender.sendMessage("ㅗ")
                                }
                            }
                            1 -> {
                                val player = BungeeCord.getInstance().getPlayer(args[1]) as UserConnection
                                player.unApplyFaking()
                            }
                        }
                    }
                }
            }
        }
    }
}