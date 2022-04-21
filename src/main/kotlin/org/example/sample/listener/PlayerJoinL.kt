package org.example.sample.listener

import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler

object PlayerJoinL : Listener {
    @EventHandler
    fun on(event : PostLoginEvent) {
        event.player.sendMessage(TextComponent("응애").apply {
            color = ChatColor.GOLD
        })
    }
}