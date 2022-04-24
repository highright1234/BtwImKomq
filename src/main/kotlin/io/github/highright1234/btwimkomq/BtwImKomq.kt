package io.github.highright1234.btwimkomq

import com.google.common.reflect.ClassPath
import net.md_5.bungee.BungeeCord
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.config.Configuration
import net.md_5.bungee.config.ConfigurationProvider
import net.md_5.bungee.config.YamlConfiguration
import net.md_5.bungee.connection.LoginResult
import net.md_5.bungee.http.HttpClient
import java.io.File
import java.util.*

class BtwImKomq : Plugin() {

    companion object {
        lateinit var instance : BtwImKomq
        private set
        private val dataForFaking : Configuration by lazy {
            ConfigurationProvider.getProvider(YamlConfiguration::class.java).load(
                File(instance.dataFolder, "data.yml")
            )
        }
        private const val MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s"
        private fun ProxiedPlayer.reconnect() {
            if (isConnected) {
                val serverInfo = server.info
                server.disconnect(TextComponent("Reconnect"))
                connect(serverInfo)
            }
        }
        val ProxiedPlayer.newName : String? get() = newNameMap[this]
        val ProxiedPlayer.newUUID : UUID? get() =
            dataForFaking.getString("$uniqueId.uniqueId", null).let { UUID.fromString(it) }
        val ProxiedPlayer.newLoginResult : LoginResult? get() = newLoginResultMap[this]
        private fun ProxiedPlayer.loadNewLoginResult(future: (error: Throwable?) -> Unit) {
            val thisAsUser = this as UserConnection
            HttpClient.get(MOJANG_PROFILE_URL.format(newUUID), thisAsUser.ch.handle.eventLoop()) { result, error ->
                error ?: run {
                    newLoginResultMap[this] = BungeeCord.getInstance().gson.fromJson(result, LoginResult::class.java)
                }
                future(error)
            }
        }

        private val newNameMap = WeakHashMap<ProxiedPlayer, String>()
        private val newLoginResultMap = WeakHashMap<ProxiedPlayer, LoginResult>()

//        private fun <V> weakMultiMap() =
//            Multimaps.newListMultimap<ProxiedPlayer, V>(WeakHashMap()) { ArrayList() }!!

        fun ProxiedPlayer.applyFaking(newUUID: UUID, future: () -> Unit = {}) {
            dataForFaking.getSection("$uniqueId").apply {
                set("uniqueId", newUUID)
            }
            loadNewLoginResult {
                future()
                if (isConnected) reconnect()
            }
        }
        fun ProxiedPlayer.unApplyFaking() {
            dataForFaking.set("$uniqueId", null)
            newNameMap -= this
            newLoginResultMap -= this
            if (isConnected) reconnect()
        }
    }

    override fun onEnable() {
        instance = this
        registerListeners()
        registerCommands()
    }

    private fun registerListeners() = packageOf("listener").objects.forEach {
        proxy.pluginManager.registerListener(this, it as Listener)
    }

    private fun registerCommands() = packageOf("command").objects.forEach {
        proxy.pluginManager.registerCommand(this, it as Command)
    }

    private fun packageOf(string : String) = Package(string)
    @JvmInline
    value class Package(private val name : String) {
        private val packageName get() = this.javaClass.`package`.name + ".$name"
        @Suppress("UnstableApiUsage")
        val objects : List<Any> get() = ClassPath.from(javaClass.classLoader)
            .getTopLevelClasses(packageName)
            .map { it.load() }
            .filter { try { it.getField("INSTANCE"); true } catch (_ : Exception) { false } }
            .map { it.getField("INSTANCE")[null] }
    }
}