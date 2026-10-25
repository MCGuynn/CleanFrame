' CleanFrame Clock for Roku: a screensaver package. Roku lists it under
' Settings > Theme > Screensavers; "Custom Settings" there opens RunScreenSaverSettings.
sub RunScreenSaver()
    showScene("ScreensaverScene")
end sub

sub RunScreenSaverSettings()
    showScene("SettingsScene")
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
