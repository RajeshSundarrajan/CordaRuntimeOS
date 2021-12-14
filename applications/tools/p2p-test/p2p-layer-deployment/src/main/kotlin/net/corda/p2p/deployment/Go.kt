package net.corda.p2p.deployment

import net.corda.p2p.deployment.commands.Bash
import net.corda.p2p.deployment.commands.ConfigureAll
import net.corda.p2p.deployment.commands.CreateStores
import net.corda.p2p.deployment.commands.Deploy
import net.corda.p2p.deployment.commands.Destroy
import net.corda.p2p.deployment.commands.Log
import net.corda.p2p.deployment.commands.Status
import net.corda.p2p.deployment.commands.UpdateIps
import net.corda.p2p.deployment.commands.simulator.Simulator
import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

@Command(
    subcommands = [
        Deploy::class,
        Destroy::class,
        Simulator::class,
        Bash::class,
        Log::class,
        CreateStores::class,
        ConfigureAll::class,
        UpdateIps::class,
        Status::class,
    ],
    header = ["Deployer for p2p layer"],
    name = "p2p-layer-deployment",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class Go
@Suppress("SpreadOperator")
fun main(args: Array<String>): Unit =
    exitProcess(CommandLine(Go()).execute(*args))