sub init()
    m.photo = m.top.findNode("photo")
    m.face = m.top.findNode("face")
    m.time = m.top.findNode("time")
    m.date = m.top.findNode("date")
    m.walls = cfWalls()
    m.wall = cfGet("wall", "0").ToInt()
    if m.wall < 0 or m.wall >= m.walls.Count() then m.wall = 0
    m.seconds = 0
    m.photo.observeField("loadStatus", "onPhotoStatus")
    showWall()
    m.timer = m.top.findNode("tick")
    m.timer.observeField("fire", "onTick")
    m.timer.control = "start"
    paint()
end sub

sub showWall()
    m.photo.opacity = 0
    m.photo.uri = cfWallUri(m.walls[m.wall].id)
end sub

sub onPhotoStatus()
    if m.photo.loadStatus = "ready" then
        m.top.findNode("fadeIn").control = "start"
    else if m.photo.loadStatus = "failed" then
        m.photo.uri = cfWallUri("misty")
    end if
end sub

sub paint()
    m.time.text = cfTime()
    m.date.text = cfDate(true)
    ' Centre (360, 330) plus a small random drift.
    m.face.translation = [360 + Rnd(240) - 120, 330 + Rnd(160) - 80]
end sub

' Each second keeps the time exact; each minute moves the clock; every ten minutes, the next photo.
sub onTick()
    m.seconds = m.seconds + 1
    m.time.text = cfTime()
    if m.seconds mod 60 = 0 then paint()
    if m.seconds mod 600 = 0 then
        m.wall = (m.wall + 1) mod m.walls.Count()
        showWall()
    end if
end sub
