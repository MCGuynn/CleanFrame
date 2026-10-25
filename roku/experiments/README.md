# Launcher experiment (not packaged)

`HomeScene` + `EcpTask` showed CleanFrame's Home on Roku with a dock of channels, using the
TV's own control API (ECP) to list and open them. On a TCL Roku TV both 127.0.0.1 and the
TV's address answered **403** to `/query/apps`, even with Network access set to Permissive:
Roku does not let an app on the TV list or open other channels. Kept here for reference;
the shipped package is the CleanFrame Clock screensaver only.
