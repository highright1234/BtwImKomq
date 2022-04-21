package org.example.sample.command

import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Command

object SampleCommand : Command("sample", null, "s") {
    override fun execute(sender: CommandSender, args: Array<out String>) {
        sender.sendMessage(TextComponent("응애"))
    }
}