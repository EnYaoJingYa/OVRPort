package moe.crx.overport.commands

import moe.crx.overport.cli.CliCommand
import moe.crx.overport.targets.TargetProfiles

object ListTargetsCommand : CliCommand() {
    override fun name() = "targets"
    override fun description() = "List supported patching targets."

    override fun printHelp(): Boolean {
        println("usage: overport targets")
        println()
        println("Lists target profiles accepted by patch --target=<value>.")
        return true
    }

    override suspend fun execute(args: List<String>): Boolean {
        if (args.isNotEmpty()) return false
        TargetProfiles.all.forEach { target ->
            println("${target.id}: ${target.displayName}")
            println("  ${target.description}")
            println("  ABIs: ${target.supportedAbis.joinToString()}")
            if (target.compatibilityPatches.isNotEmpty()) {
                println("  Additional patches: ${target.compatibilityPatches.joinToString()}")
            }
        }
        return true
    }
}
