' CleanFrame for Roku. One package, two entry points:
'   Main            - the Home-style screen you open from Roku Home
'   RunScreenSaver  - "CleanFrame Clock", chosen in Settings > Theme > Screensavers
sub Main(args as Dynamic)
    showScene("HomeScene")
end sub

sub RunScreenSaver()
    showScene("ScreensaverScene")
end sub

sub showScene(name as String)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    screen.CreateScene(name)
    screen.show()
    while true
        msg = wait(0, port)
        if type(msg) = "roSGScreenEvent" and msg.isScreenClosed() then return
    end while
end sub
