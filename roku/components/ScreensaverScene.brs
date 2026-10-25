sub init()
    m.photo = m.top.findNode("photo")
    m.face = m.top.findNode("face")
    m.time = m.top.findNode("time")
    m.date = m.top.findNode("date")
    m.credit = m.top.findNode("credit")
    m.walls = cfWalls()
    m.interval = cfGet("interval", "10").ToInt() * 60
    if m.interval < 60 then m.interval = 600
    m.seconds = 0
    m.unsplash = []
    m.useUnsplash = cfGet("source", "cleanframe") <> "cleanframe"
    if m.useUnsplash then
        cached = ParseJson(cfGet("unsplash_cache", "[]"))
        if cached <> invalid then m.unsplash = cached
        ' Fresh photos for next time; this run starts with the saved ones.
        m.refresh = CreateObject("roSGNode", "UnsplashTask")
        m.refresh.command = { kind: "photos" }
        m.refresh.control = "RUN"
    end if
    if m.useUnsplash and m.unsplash.Count() > 0 then
        m.index = Rnd(m.unsplash.Count()) - 1
    else
        m.index = cfGet("wall", "0").ToInt()
    end if
    m.photo.observeField("loadStatus", "onPhotoStatus")
    showPhoto()
    m.timer = m.top.findNode("tick")
    m.timer.observeField("fire", "onTick")
    m.timer.control = "start"
    paint()
end sub

sub showPhoto()
    m.photo.opacity = 0
    if m.useUnsplash and m.unsplash.Count() > 0 then
        item = m.unsplash[m.index mod m.unsplash.Count()]
        m.photo.uri = item.url
        m.credit.text = "Photo: " + item.author + " / Unsplash"
        ' Unsplash asks apps to report each photo they show.
        if item.ping <> invalid and item.ping <> "" then
            m.pingTask = CreateObject("roSGNode", "UnsplashTask")
            m.pingTask.command = { kind: "ping", url: item.ping }
            m.pingTask.control = "RUN"
        end if
    else
        m.photo.uri = cfWallUri(m.walls[m.index mod m.walls.Count()].id)
        m.credit.text = ""
    end if
end sub

sub onPhotoStatus()
    if m.photo.loadStatus = "ready" then
        m.top.findNode("fadeIn").control = "start"
    else if m.photo.loadStatus = "failed" then
        m.credit.text = ""
        m.photo.uri = cfWallUri("misty")
    end if
end sub

sub paint()
    m.time.text = cfTime()
    m.date.text = cfDate(true)
    ' Centre (360, 330) plus a small random drift.
    m.face.translation = [360 + Rnd(240) - 120, 330 + Rnd(160) - 80]
end sub

' Each second keeps the time exact; each minute moves the clock; then the next photo on schedule.
sub onTick()
    m.seconds = m.seconds + 1
    m.time.text = cfTime()
    if m.seconds mod 60 = 0 then paint()
    if m.seconds mod m.interval = 0 then
        m.index = m.index + 1
        showPhoto()
    end if
end sub
