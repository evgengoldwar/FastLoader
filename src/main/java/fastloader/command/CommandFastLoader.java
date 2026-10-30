package fastloader.command;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import fastloader.pregen.ChunkPregenerator;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.ProfileReport;

public class CommandFastLoader extends CommandBase {

    private static final String USAGE = "/fastloader profile <on|off|status|report|reset>"
        + " | /fastloader pregen <radius> [dim] [chunkX chunkZ] | /fastloader pregen <stop|status>";

    @Override
    public String getCommandName() {
        return "fastloader";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("fl");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(USAGE);
        if ("profile".equalsIgnoreCase(args[0])) {
            profile(sender, args.length > 1 ? args[1].toLowerCase() : "status");
        } else if ("pregen".equalsIgnoreCase(args[0])) {
            pregen(sender, args);
        } else {
            throw new WrongUsageException(USAGE);
        }
    }

    private void profile(ICommandSender sender, String sub) {
        switch (sub) {
            case "on":
                ChunkProfiler.setEnabled(true);
                send(sender, "Chunk profiler enabled (interval reset)");
                break;
            case "off":
                ChunkProfiler.setEnabled(false);
                send(sender, "Chunk profiler disabled");
                break;
            case "reset":
                ChunkProfiler.resetInterval();
                send(sender, "Chunk profiler interval reset");
                break;
            case "report": {
                File f = ProfileReport.manual();
                send(sender, ProfileReport.shortStatus());
                send(sender, f == null ? "Full report written to the server log" : "Full report: " + f.getPath());
                break;
            }
            case "status":
                send(sender, (ChunkProfiler.enabled ? "[on] " : "[off] ") + ProfileReport.shortStatus());
                break;
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private void pregen(ICommandSender sender, String[] args) {
        if (args.length < 2) throw new WrongUsageException(USAGE);
        if ("stop".equalsIgnoreCase(args[1])) {
            send(sender, ChunkPregenerator.INSTANCE.stop(sender));
            return;
        }
        if ("status".equalsIgnoreCase(args[1])) {
            send(sender, ChunkPregenerator.INSTANCE.status());
            return;
        }
        int radius = parseIntBounded(sender, args[1], 0, 2000);
        World world = sender.getEntityWorld();
        int dim = args.length > 2 ? parseInt(sender, args[2]) : world == null ? 0 : world.provider.dimensionId;
        int cx, cz;
        if (args.length > 4) {
            cx = parseInt(sender, args[3]);
            cz = parseInt(sender, args[4]);
        } else {
            ChunkCoordinates c = sender.getPlayerCoordinates();
            cx = c.posX >> 4;
            cz = c.posZ >> 4;
        }
        send(sender, ChunkPregenerator.INSTANCE.start(dim, cx, cz, radius));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "profile", "pregen");
        if (args.length == 2 && "profile".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, "on", "off", "status", "report", "reset");
        if (args.length == 2 && "pregen".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, "stop", "status", "32", "64", "128");
        return null;
    }

    private static void send(ICommandSender sender, String msg) {
        for (String line : Arrays.asList(msg.split("\n"))) {
            sender.addChatMessage(new ChatComponentText("[FastLoader] " + line));
        }
    }
}
