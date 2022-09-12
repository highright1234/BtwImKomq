package io.github.highright1234.btwimkomq

import com.google.common.collect.MapMaker
import com.google.common.reflect.ClassPath
import io.github.highright1234.btwimkomq.listener.ServerConnectL
import net.md_5.bungee.BungeeCord
import net.md_5.bungee.ServerConnection
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ServerConnectRequest
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.ServerConnectEvent
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.config.Configuration
import net.md_5.bungee.config.ConfigurationProvider
import net.md_5.bungee.config.YamlConfiguration
import net.md_5.bungee.connection.LoginResult
import net.md_5.bungee.http.HttpClient
import net.md_5.bungee.netty.HandlerBoss
import java.io.File
import java.util.*

class BtwImKomq : Plugin() {

    companion object {
        lateinit var instance : BtwImKomq
        private set
        private val dataFile : File by lazy {
            File(instance.dataFolder, "data.yml").also {
                if (!it.parentFile.exists()) {
                    it.parentFile.mkdir()
                    if (!it.exists()) {
                        it.createNewFile()
                    }
                }
            }
        }
        private val dataForFaking : Configuration by lazy {
            ConfigurationProvider.getProvider(
                YamlConfiguration::class.java
            ).load(dataFile)
        }
        private const val MOJANG_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false"
        private fun ProxiedPlayer.reconnect() {
            if (isConnected) {
                val serverCon = server as ServerConnection
                serverCon.info.removePlayer(this)
                val reason = ServerConnectEvent.Reason.PLUGIN
                val request = ServerConnectRequest.builder()
                    .target(serverCon.info)
                    .connectTimeout(5000)
                    .retry(true)
                    .reason(reason)
                    .sendFeedback(false)
                    .callback { _, _ ->  }
                    .build()
                ServerConnectL.on(ServerConnectEvent(this, serverCon.info, reason, request))
                val handlerBoss = serverCon.ch.handle.pipeline().get(HandlerBoss::class.java)
                HandlerBoss::class.java.getDeclaredField("handler").apply {
                    isAccessible = true
                }.set(handlerBoss, null)
                server.disconnect(TextComponent("Reconnect"))
            }
        }
        val ProxiedPlayer.newName : String? get() = newNameMap[this]
        val ProxiedPlayer.newUUID : UUID? get() =
            dataForFaking.getString("$uniqueId.uniqueId", null)?.let { UUID.fromString(it) }
        val ProxiedPlayer.newLoginResult : LoginResult? get() = newLoginResultMap[this]
        fun ProxiedPlayer.loadNewLoginResult(future: (error: Throwable?) -> Unit) {
            val thisAsUser = this as UserConnection
            HttpClient.get(MOJANG_PROFILE_URL.format("$newUUID".replace("-", "")), thisAsUser.ch.handle.eventLoop()) { result, error ->
                error ?: run {
                    val newLoginResult = BungeeCord.getInstance().gson.fromJson(result, LoginResult::class.java)!!
                    newLoginResultMap[this] = newLoginResult
                    newNameMap[this] = newLoginResult.name
                }
                println(result)
                future(error)
            }
        }

        private val newNameMap = MapMaker().weakKeys().makeMap<ProxiedPlayer, String>()
        private val newLoginResultMap = MapMaker().weakKeys().makeMap<ProxiedPlayer, LoginResult>()

//        private fun <V> weakMultiMap() =
//            Multimaps.newListMultimap<ProxiedPlayer, V>(WeakHashMap()) { ArrayList() }!!

        fun ProxiedPlayer.applyFaking(newUUID: UUID, future: () -> Unit = {}) {
            dataForFaking.set("$uniqueId.uniqueId", "$newUUID")
            loadNewLoginResult {
                future()
                reconnect()
            }
        }
        fun ProxiedPlayer.unApplyFaking() {
            dataForFaking.set("$uniqueId.uniqueId", null)
            newNameMap -= this
            newLoginResultMap -= this
            reconnect()
        }
    }

    override fun onEnable() {
        instance = this
        registerListeners()
        registerCommands()
    }

    override fun onDisable() {
        ConfigurationProvider.getProvider(YamlConfiguration::class.java).save(dataForFaking, dataFile)
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