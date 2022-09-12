package io.github.highright1234.btwimkomq.listener

import io.github.highright1234.btwimkomq.BtwImKomq.Companion.loadNewLoginResult
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.newLoginResult
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.newUUID
import io.github.highright1234.btwimkomq.DataModificationServerConnector
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.util.internal.PlatformDependent
import net.md_5.bungee.BungeeCord
import net.md_5.bungee.BungeeServerInfo
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.Callback
import net.md_5.bungee.api.ServerConnectRequest
import net.md_5.bungee.api.event.ServerConnectEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import net.md_5.bungee.event.EventPriority
import net.md_5.bungee.netty.HandlerBoss
import net.md_5.bungee.netty.PipelineUtils
import net.md_5.bungee.protocol.MinecraftDecoder
import net.md_5.bungee.protocol.MinecraftEncoder
import net.md_5.bungee.protocol.Protocol
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

object ServerConnectL : Listener {
    private val passingEvents = ConcurrentLinkedQueue<ServerConnectEvent>()
    @EventHandler(priority = EventPriority.LOWEST)
    fun on(event : ServerConnectEvent) {
        println("응애")
        val user = event.player as UserConnection
        user.newUUID ?: return
        if (passingEvents.contains(event)) return
        event.isCancelled = true
        user.newLoginResult?.let {
            user.connectWithFakeData(event)
        } ?: run {
            user.loadNewLoginResult {
                it ?: user.connectWithFakeData(event)
            }
        }
    }

    @Suppress("DEPRECATION")
    fun UserConnection.connectWithFakeData(event : ServerConnectEvent) {
        val request = event.request
        fun connectionFailMessage(throwable: Throwable) {
            javaClass.getDeclaredMethod("connectionFailMessage", Throwable::class.java).apply {
                isAccessible = true
            }.invoke(this, throwable)
        }
        val dimensionChange = javaClass.getDeclaredField("dimensionChange").apply {
            isAccessible = true
        }[this] as Boolean
        val callback: Callback<ServerConnectRequest.Result>? = request.callback
        val newEvent = ServerConnectEvent(this, request.target, request.reason, request)
        passingEvents += newEvent
        val bungee = BungeeCord.getInstance()
        val isCancelled = bungee.pluginManager.callEvent(newEvent).isCancelled
        passingEvents -= newEvent
        println("됐음")
        if (isCancelled) {
            callback?.done(ServerConnectRequest.Result.EVENT_CANCEL, null)
            check(!(server == null && !ch.isClosing)) { "Cancelled ServerConnectEvent with no server or disconnect." }
            return
        }

        val target = event.target as BungeeServerInfo // Update in case the event changed target

        if (server != null && server.info == target) {
            callback?.done(ServerConnectRequest.Result.ALREADY_CONNECTED, null)
            if (request.isSendFeedback) sendMessage(bungee.getTranslation("already_connected")) // Waterfall
            return
        }
        if (pendingConnects.contains(target)) {
            callback?.done(ServerConnectRequest.Result.ALREADY_CONNECTING, null)
            if (request.isSendFeedback) sendMessage(bungee.getTranslation("already_connecting")) // Waterfall
            return
        }
        println("체크한다 개새꺄")

        pendingConnects.add(target)

        val initializer: ChannelInitializer<*> = object : ChannelInitializer<Channel>() {
            @Throws(Exception::class)
            override fun initChannel(ch: Channel) {
                PipelineUtils.BASE.initChannel(ch)
                ch.pipeline().addAfter(
                    PipelineUtils.FRAME_DECODER,
                    PipelineUtils.PACKET_DECODER,
                    MinecraftDecoder(Protocol.HANDSHAKE, false, pendingConnection.version)
                )
                ch.pipeline().addAfter(
                    PipelineUtils.FRAME_PREPENDER,
                    PipelineUtils.PACKET_ENCODER,
                    MinecraftEncoder(Protocol.HANDSHAKE, false, pendingConnection.version)
                )
                ch.pipeline().get(HandlerBoss::class.java)
                    .setHandler(DataModificationServerConnector(bungee, this@connectWithFakeData, target))
            }
        }
        val listener = ChannelFutureListener { future ->
            callback?.done(
                if (future.isSuccess) ServerConnectRequest.Result.SUCCESS else ServerConnectRequest.Result.FAIL,
                future.cause()
            )
            if (!future.isSuccess) {
                future.channel().close()
                pendingConnects.remove(target)
                val def = updateAndGetNextServer(target)
                if (request.isRetry && def != null && (server == null || def !== server.info)) {
                    if (request.isSendFeedback) sendMessage(bungee.getTranslation("fallback_lobby")) // Waterfall
                    connect(
                        def,
                        null,
                        true,
                        ServerConnectEvent.Reason.LOBBY_FALLBACK,
                        request.connectTimeout,
                        request.isSendFeedback
                    ) // Waterfall
                } else if (dimensionChange) {
                    disconnect(bungee.getTranslation("fallback_kick", connectionFailMessage(future.cause())))
                } else {
                    if (request.isSendFeedback) sendMessage(
                        bungee.getTranslation(
                            "fallback_kick",
                            connectionFailMessage(future.cause())
                        )
                    )
                }
            }
        }
        val b = Bootstrap()
            .channel(PipelineUtils.getChannel(target.address))
            .group(ch.handle.eventLoop())
            .handler(initializer)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, request.connectTimeout)
            .remoteAddress(target.address)
        // Windows is bugged, multi homed users will just have to live with random connecting IPs
        // Windows is bugged, multi homed users will just have to live with random connecting IPs
        if (pendingConnection.listener.isSetLocalAddress && !PlatformDependent.isWindows() && pendingConnection.listener.socketAddress is InetSocketAddress) {
            b.localAddress(pendingConnection.listener.host.hostString, 0)
        }
        b.connect().addListener(listener)
    }
}