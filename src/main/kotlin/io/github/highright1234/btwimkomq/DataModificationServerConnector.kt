package io.github.highright1234.btwimkomq

import io.github.highright1234.btwimkomq.BtwImKomq.Companion.newLoginResult
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.newName
import io.github.highright1234.btwimkomq.BtwImKomq.Companion.newUUID
import net.md_5.bungee.BungeeCord
import net.md_5.bungee.BungeeServerInfo
import net.md_5.bungee.ServerConnector
import net.md_5.bungee.UserConnection
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.connection.LoginResult
import net.md_5.bungee.forge.ForgeServerHandler
import net.md_5.bungee.netty.ChannelWrapper
import net.md_5.bungee.protocol.Protocol
import net.md_5.bungee.protocol.packet.Handshake
import net.md_5.bungee.protocol.packet.LoginRequest
import net.md_5.bungee.util.AddressUtil
import java.net.InetSocketAddress
import kotlin.reflect.KProperty

class DataModificationServerConnector(
    bungee : ProxyServer, user : UserConnection, target : BungeeServerInfo
) : ServerConnector(bungee, user, target) {
    private var ch : ChannelWrapper by SuperField()
    private var user : UserConnection by SuperField()
    private var target : BungeeServerInfo by SuperField()
    @get:JvmName("getHandshakeHandler0")
    private var handshakeHandler : ForgeServerHandler by SuperField()
    override fun connected(channel: ChannelWrapper) {
        ch = channel
        handshakeHandler = ForgeServerHandler(user, ch, target)
        val originalHandshake = user.pendingConnection.handshake
        val copiedHandshake =
            Handshake(originalHandshake.protocolVersion, originalHandshake.host, originalHandshake.port, 2)
        if (BungeeCord.getInstance().config.isIpForward && user.socketAddress is InetSocketAddress) {
            var newHost =
                copiedHandshake.host + "\u0000" + AddressUtil.sanitizeAddress(user.address) + "\u0000" + user.newUUID!!
            val profile = user.newLoginResult
            var properties = arrayOfNulls<LoginResult.Property>(0)
            if (profile != null && profile.properties != null && profile.properties.isNotEmpty()) {
                properties = profile.properties
            }
            if (user.forgeClientHandler.isFmlTokenInHandshake) {
                val newp = properties.copyOf(properties.size + 2)
                newp[newp.size - 2] = LoginResult.Property("forgeClient", "true", null as String?)
                newp[newp.size - 1] = LoginResult.Property(
                    "extraData",
                    user.extraDataInHandshake.replace("\u0000".toRegex(), "\u0001"),
                    ""
                )
                properties = newp
            }
            if (properties.isNotEmpty()) {
                newHost = newHost + "\u0000" + BungeeCord.getInstance().gson.toJson(properties)
            }
            copiedHandshake.host = newHost
        } else if (user.extraDataInHandshake.isNotEmpty()) {
            copiedHandshake.host = copiedHandshake.host + user.extraDataInHandshake
        }

        channel.write(copiedHandshake)
        channel.setProtocol(Protocol.LOGIN)
        channel.write(LoginRequest(user.newName))
    }


    inner class SuperField<T> {
        operator fun getValue(thisRef: DataModificationServerConnector, property: KProperty<*>): T {
            @Suppress("UNCHECKED_CAST")
            return thisRef.javaClass.superclass
                .getDeclaredField(property.name).apply { isAccessible = true }[thisRef] as T
        }

        operator fun setValue(thisRef: DataModificationServerConnector, property: KProperty<*>, newValue: T) {
            thisRef.javaClass.superclass
                .getDeclaredField(property.name).apply { isAccessible = true }.set(thisRef, newValue)
        }
    }
}