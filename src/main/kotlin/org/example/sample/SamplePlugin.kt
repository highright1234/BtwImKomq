package org.example.sample

import com.google.common.reflect.ClassPath
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin

class SamplePlugin : Plugin() {

    override fun onEnable() {
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