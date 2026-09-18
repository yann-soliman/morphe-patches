# Naolib vélo 3.6.1

Package: `com.jcdecaux.vls.nantes`. Input: complete split APK archive (APKS/XAPK).

## Remove parking distance limit

The app compares GPS-to-parking distance with the contract's `parking.open.distance`
(default 50 metres if absent). The patch removes only the local rejection branch
in the parking-opening use case. Location permission/acquisition, authentication,
account identity, server-side subscriptions and the opening request remain intact.
It does not change bicycle rental proximity checks or spoof GPS coordinates.

Static analysis cannot establish whether a server enforces additional checks.
A network connection and valid parking access are still required.

Device validation:

1. Enable only this patch on the original 3.6.1 archive.
2. Check startup, login and parking details.
3. With valid access to your parking, verify that a distant GPS position no longer
   produces the local proximity message. Check the server's actual response.
4. Verify normal nearby opening and permission-denied handling.

## Keep parking list

The OpenData parking repository reads the Room cache, then refreshes from the
network. Its cache writer deletes all old rows before inserting the received list.
A successful but empty response therefore removes cached rows and can clear the
map just after the cached markers appear.

The patch filters empty **network parking-list emissions** before cache writes
and UI delivery. Initial empty cache results remain valid. Nonempty responses
still update the entire list, so legitimate individual removals are reflected.
Existing network retry/error handling is retained. The existing nonempty-list
predicate is made public so the parking repository can legally call it across
packages; its behavior and other uses are unchanged.

This deliberately treats an entirely empty catalogue as temporarily unavailable:
a legitimate removal of every parking would leave the previous list displayed.
Cached availability and status may be stale; the patch does not create live data.
It does not add an offline cache of detailed parking records or enable offline
opening. It cannot reconstruct a list that was already erased before patching;
first load a nonempty list successfully.

Device validation:

1. Load the map successfully so the existing cache contains parkings.
2. Reopen the map and check whether the previously disappearing markers remain.
3. In a controlled network test, return an empty parking catalogue: cached rows
   and visible markers must remain. A later nonempty response must update them.
4. Test cold start with no cache, network error, and subsequent recovery.
5. Check bicycle-station updates and normal parking opening independently.

Compilation and static bytecode inspection do not replace these device checks.
