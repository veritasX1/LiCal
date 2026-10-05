import sys

if "--erinnerungen" in sys.argv:  # the alert service needs no GTK – lean in the background
    from .reminders import run
    run()
    raise SystemExit(0)

from .app import main

raise SystemExit(main())
