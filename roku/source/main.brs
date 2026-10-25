' CleanFrame Clock for Roku: a screensaver only. Roku lists screensaver packages under
' Settings > Theme > Screensavers; a screensaver package has RunScreenSaver and no Main.
sub RunScreenSaver()
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    screen.CreateScene("ScreensaverScene")
    screen.show()
    while true
        msg = wait(0, port)
        if type(msg) = "roSGScreenEvent" and msg.isScreenClosed() then return
    end while
end sub
