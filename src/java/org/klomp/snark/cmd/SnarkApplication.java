package org.klomp.snark.cmd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Timer;
import java.util.TimerTask;

import org.klomp.snark.CoordinatorListener;
import org.klomp.snark.Peer;
import org.klomp.snark.PeerMonitorTask;
import org.klomp.snark.ShutdownListener;
import org.klomp.snark.Snark;
import org.klomp.snark.SnarkShutdown;
import org.klomp.snark.StorageListener;

public class SnarkApplication
{
    protected static final String newline =
        System.getProperty("line.separator");

    protected static final String copyright =
        "The Hunting of the Snark Project - " +
        "Copyright (C) 2003 Mark J. Wielaard, (c) 2006 Three Rings Design" +
        newline +
        newline +
        "Snark comes with ABSOLUTELY NO WARRANTY.  This is free software, and" +
        newline +
        "you are welcome to redistribute it under certain conditions; read the" +
        newline + "COPYING file for details.";

    protected  static final String usage =
        "Press return for help. Type \"quit\" and return to stop.";

    protected static final String help = "Commands: 'info', 'list', 'quit'.";

    /**
     * A basic command line interface to the Snark library.
     */
    public static void main(String[] args)
    {
        System.out.println(copyright);
        System.out.println();

        // Parse debug, share/ip and torrent file options.
        Snark snark = parseArguments(args);
        boolean interactive = true;
        for (String arg : args) {
            if (arg.equals("--no-commands")) {
                interactive = false;
            }
        }
;
        snark.setupNetwork();
        snark.collectPieces();

        ShutdownListener listener = new ShutdownListener() {
            // documentation inherited from interface ShutdownListener
            public void shutdown()
            {
                // Should not be necessary since all non-deamon threads should
                // have died. But in reality this does not always happen.
                System.exit(0);
            }
        };
        SnarkShutdown hook = new SnarkShutdown(snark.storage,
                snark.coordinator, snark.acceptor, snark.trackerclient,
                listener);
        Runtime.getRuntime().addShutdownHook(hook);

        Timer timer = new Timer(true);
        TimerTask monitor = new PeerMonitorTask(snark.coordinator);
        timer.schedule(monitor, PeerMonitorTask.MONITOR_PERIOD,
                PeerMonitorTask.MONITOR_PERIOD);

        // Start command interpreter
        if (interactive) {
            doInteractive(snark, hook);
        }
    }

    /**
     * Initializes the user-interactive readline interface to Snark
     */
    protected static void doInteractive (Snark snark, SnarkShutdown hook)
    {
        boolean quit = false;

        System.out.println();
        System.out.println(usage);
        System.out.println();

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    System.in));
            String line = br.readLine();
            while (!quit && line != null) {
                line = line.toLowerCase();
                if ("quit".equals(line)) {
                    quit = true;
                } else if ("list".equals(line)) {
                    synchronized (snark.coordinator.peers) {
                        System.out.println(snark.coordinator.peers.size()
                                + " peers -" + " (i)nterested,"
                                + " (I)nteresting," + " (c)hoking,"
                                + " (C)hoked:");
                        Iterator it = snark.coordinator.peers.iterator();
                        while (it.hasNext()) {
                            Peer peer = (Peer)it.next();
                            System.out.println(peer);
                            System.out.println("\ti: "
                                    + peer.isInterested() + " I: "
                                    + peer.isInteresting() + " c: "
                                    + peer.isChoking() + " C: "
                                    + peer.isChoked());
                        }
                    }
                } else if ("info".equals(line)) {
                    System.out.println("Name: " + snark.meta.getName());
                    System.out.println("Torrent: " + snark.torrent);
                    System.out.println("Tracker: "
                            + snark.meta.getAnnounce());
                    List files = snark.meta.getFiles();
                    System.out.println("Files: "
                            + ((files == null) ? 1 : files.size()));
                    System.out.println("Pieces: " + snark.meta.getPieces());
                    System.out.println("Piece size: "
                            + snark.meta.getPieceLength(0) / 1024 + " KB");
                    System.out.println("Total size: "
                            + snark.meta.getTotalLength() / (1024 * 1024)
                            + " MB");
                } else if ("".equals(line) || "help".equals(line)) {
                    System.out.println(usage);
                    System.out.println(help);
                } else {
                    System.out.println("Unknown command: " + line);
                    System.out.println(usage);
                }

                if (!quit) {
                    System.out.println();
                    line = br.readLine();
                }
            }
        } catch (IOException ioe) {
            log.log(Level.SEVERE, "ERROR while reading stdin", ioe);
        }

        // Explicit shutdown.
        Runtime.getRuntime().removeShutdownHook(hook);
        hook.start();
    }

    /**
     * Prints messages about proper usage of the Snark application.
     */
    protected static void usage(String s)
    {
        if (s != null) {
            System.out.println("snark: " + s);
        }
        System.out
                .println("Usage: snark [--debug [level]] [--no-commands] [--port <port>]");
        System.out
                .println("             [--share (<ip>|<host>)] (<url>|<file>|<dir>)");
        System.out.println("  --debug\tShows some extra info and stacktraces");
        System.out.println("    level\tHow much debug details to show");
        System.out.println("         \t(defaults to " + Level.SEVERE
                + ", with --debug to " + Level.INFO + ", highest level is " + Level.ALL
                + ").");
        System.out
                .println("  --no-commands\tDon't read interactive commands or show usage info.");
        System.out
                .println("  --port\tThe port to listen on for incomming connections");
        System.out
                .println("        \t(if not given defaults to first free port between "
                        + Snark.MIN_PORT + "-" + Snark.MAX_PORT + ").");
        System.out
                .println("  --share\tStart torrent tracker on <ip> address or <host> name.");
        System.out
                .println("  <url>  \tURL pointing to .torrent metainfo file to download/share.");
        System.out
                .println("  <file> \tEither a local .torrent metainfo file to download");
        System.out.println("         \tor (with --share) a file to share.");
        System.out
                .println("  <dir>  \tA directory with files to share (needs --share).");
        System.exit(-1);
    }

    /**
     * A convenience method for parsing arguments passed via the command line
     * where no overriding of the listeners is required.
     */
    public static Snark parseArguments(String[] args)
    {
        return parseArguments(args, null, null);
    }

    /**
     * Sets debug, ip and torrent variables then creates a Snark instance. Calls
     * usage(), which terminates the program, if non-valid argument list. The
     * given listeners will be passed to all components that take one.
     */
    public static Snark parseArguments(String[] args,
            StorageListener slistener, CoordinatorListener clistener)
    {
        int user_port = -1;
        String ip = null;
        String torrent = null;
        Level level = Level.INFO;

        int i = 0;
        while (i < args.length) {
            if (args[i].equals("--debug")) {
                level = Level.FINE;
                i++;

                // Try if there is an level argument.
                if (i < args.length) {
                    try {
                        level = Level.parse(args[i]);
                    } catch (IllegalArgumentException iae) {
                        // continue parsing arguments
                    }
                }
            } else if (args[i].equals("--port")) {
                if (args.length - 1 < i + 1) {
                    usage("--port needs port number to listen on");
                }
                try {
                    user_port = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException nfe) {
                    usage("--port argument must be a number (" + nfe + ")");
                }
                i += 2;
            } else if (args[i].equals("--share")) {
                if (args.length - 1 < i + 1) {
                    usage("--share needs local ip-address or host-name");
                }
                ip = args[i + 1];
                i += 2;
            } else if (args[i].equals("--no-commands")) {
                // ignore, processed elsewhere.
                i++;
            } else {
                torrent = args[i];
                i++;
                break;
            }
        }
        log.setLevel(level);
        Snark.setLogLevel(level);

        if (torrent == null || i != args.length) {
            if (torrent != null && torrent.startsWith("-")) {
                usage("Unknown option '" + torrent + "'.");
            } else {
                usage("Need exactly one <url>, <file> or <dir>.");
            }
        }

        Snark snark = new Snark(torrent, ip, user_port, slistener, clistener);
        return snark;
    }

    /** The Java logger used to process our log events. */
    protected static final Logger log =
        Logger.getLogger("org.klomp.snark.cmd");
}
