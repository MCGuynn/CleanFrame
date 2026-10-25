' CleanFrame Home for Roku.
'   Left/Right  move along the row      OK  open the channel or input
'   *           next wallpaper          Back  leave (to Roku Home)

sub init()
    m.photo = m.top.findNode("photo")
    m.glass = m.top.findNode("glass")
    m.clock = m.top.findNode("clock")
    m.date = m.top.findNode("date")
    m.hint = m.top.findNode("hint")
    m.status = m.top.findNode("status")
    m.track = m.top.findNode("track")
    m.walls = cfWalls()
    m.wall = cfGet("wall", "0").ToInt()
    if m.wall < 0 or m.wall >= m.walls.Count() then m.wall = 0
    m.apps = []
    m.tiles = []
    m.focus = 0
    m.first = 0
    m.host = ""
    m.hint.text = "*  Wallpaper"
    m.status.text = "Looking for your channels" + Chr(8230)

    m.photo.observeField("loadStatus", "onPhotoStatus")
    showWall()

    m.tick = m.top.findNode("tick")
    m.tick.observeField("fire", "onTick")
    m.tick.control = "start"
    onTick()

    m.ecp = CreateObject("roSGNode", "EcpTask")
    m.ecp.observeField("result", "onEcp")
    m.ecp.command = { kind: "apps" }
    m.ecp.control = "RUN"
    m.top.setFocus(true)
end sub

sub onTick()
    m.clock.text = cfTime()
    m.date.text = cfDate(false)
end sub

sub showWall()
    w = m.walls[m.wall]
    m.photo.uri = cfWallUri(w.id)
    m.glass.uri = "pkg:/images/glass/" + w.id + ".jpg"
end sub

' Offline or GitHub unreachable: fall back to the photo inside the package.
sub onPhotoStatus()
    if m.photo.loadStatus = "failed" and m.walls[m.wall].id <> "misty" then
        m.photo.uri = cfWallUri("misty")
        m.glass.uri = "pkg:/images/glass/misty.jpg"
    end if
end sub

sub onEcp()
    r = m.ecp.result
    if r.kind = "apps" then
        if r.ok then
            m.host = r.host
            m.apps = r.apps
            buildTiles()
            if m.apps.Count() = 0 then m.status.text = "No channels found."
            if m.apps.Count() > 0 then m.status.text = ""
        else
            m.status.text = "Roku didn't share the channel list (" + r.detail + "). Try Settings > System > Advanced system settings > Control by mobile apps > Network access > Permissive, then open CleanFrame again."
        end if
    else if r.kind = "launch" then
        if r.ok then
            m.status.text = ""
        else
            m.status.text = "Roku wouldn't open it from here (" + r.detail + "). Permissive network access may allow it."
        end if
    end if
end sub

sub buildTiles()
    m.track.removeChildrenIndex(m.track.getChildCount(), 0)
    m.tiles = []
    for i = 0 to m.apps.Count() - 1
        app = m.apps[i]
        tile = CreateObject("roSGNode", "Group")
        tile.translation = [i * 296, 0]

        plate = CreateObject("roSGNode", "Poster")
        plate.uri = "pkg:/images/plate.9.png"
        plate.width = 288
        plate.height = 200
        plate.opacity = 0
        tile.appendChild(plate)

        mark = cfMarkFor(app.name)
        art = CreateObject("roSGNode", "Poster")
        if mark <> "" then
            art.uri = "pkg:/images/marks/" + mark + ".png"
            art.width = 120
            art.height = 72
            art.translation = [84, 26]
        else
            ' Channels without a CleanFrame mark use their own Roku icon.
            art.uri = "http://" + m.host + ":8060/query/icon/" + app.id
            art.loadDisplayMode = "scaleToFit"
            art.width = 128
            art.height = 96
            art.translation = [80, 14]
        end if
        tile.appendChild(art)

        label = CreateObject("roSGNode", "Label")
        label.text = app.name
        label.width = 264
        label.translation = [12, 124]
        label.horizAlign = "center"
        label.color = "0xF6F1E7FF"
        font = CreateObject("roSGNode", "Font")
        font.uri = "pkg:/fonts/Geist-Medium.ttf"
        font.size = 32
        label.font = font
        tile.appendChild(label)

        m.track.appendChild(tile)
        m.tiles.Push({ plate: plate, label: label })
    end for
    m.focus = 0
    m.first = 0
    updateFocus()
end sub

' About five and a half tiles fit; the row scrolls so the focused one is always whole.
sub updateFocus()
    visible = 5
    if m.focus < m.first then m.first = m.focus
    if m.focus > m.first + visible - 1 then m.first = m.focus - visible + 1
    m.track.translation = [112 - m.first * 296, 692]
    for i = 0 to m.tiles.Count() - 1
        isFocus = (i = m.focus)
        if isFocus then
            m.tiles[i].plate.opacity = 1
            m.tiles[i].label.color = "0xFFFFFFFF"
        else
            m.tiles[i].plate.opacity = 0
            m.tiles[i].label.color = "0xF6F1E7C0"
        end if
    end for
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "right" and m.focus < m.tiles.Count() - 1 then
        m.focus = m.focus + 1
        updateFocus()
        return true
    else if key = "left" and m.focus > 0 then
        m.focus = m.focus - 1
        updateFocus()
        return true
    else if key = "OK" and m.apps.Count() > 0 then
        m.status.text = "Opening " + m.apps[m.focus].name + Chr(8230)
        m.ecp.command = { kind: "launch", id: m.apps[m.focus].id }
        return true
    else if key = "options" then
        m.wall = (m.wall + 1) mod m.walls.Count()
        cfSet("wall", m.wall.ToStr())
        showWall()
        return true
    end if
    return false
end function
