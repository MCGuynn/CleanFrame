' Shared helpers for both scenes.

function cfWalls() as Object
    walls = ParseJson(ReadAsciiFile("pkg:/source/walls.json"))
    if walls = invalid or walls.Count() = 0 then walls = [{ id: "misty", name: "Misty coast" }]
    return walls
end function

' One photo ships in the package; the others load from the CleanFrame repo on GitHub.
function cfWallUri(id as String) as String
    if id = "misty" then return "pkg:/images/walls/misty.jpg"
    return "https://raw.githubusercontent.com/MCGuynn/CleanFrame/main/app/assets/wallpapers/" + id + ".jpg"
end function

function cfGet(key as String, fallback as String) as String
    reg = CreateObject("roRegistrySection", "cleanframe")
    if reg.Exists(key) then return reg.Read(key)
    return fallback
end function

sub cfSet(key as String, value as String)
    reg = CreateObject("roRegistrySection", "cleanframe")
    reg.Write(key, value)
    reg.Flush()
end sub

function cfTwo(n as Integer) as String
    if n < 10 then return "0" + n.ToStr()
    return n.ToStr()
end function

function cfTime() as String
    dt = CreateObject("roDateTime")
    dt.ToLocalTime()
    h = dt.GetHours()
    mi = dt.GetMinutes()
    if cfGet("clock", "12") = "24" then return cfTwo(h) + ":" + cfTwo(mi)
    h12 = h mod 12
    if h12 = 0 then h12 = 12
    return h12.ToStr() + ":" + cfTwo(mi)
end function

function cfDate(long as Boolean) as String
    dt = CreateObject("roDateTime")
    dt.ToLocalTime()
    months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
    wd = dt.GetWeekday()
    mo = months[dt.GetMonth() - 1]
    if long then return wd + ", " + mo + " " + dt.GetDayOfMonth().ToStr()
    return Left(wd, 3) + ", " + Left(mo, 3) + " " + dt.GetDayOfMonth().ToStr()
end function

' White CleanFrame mark for a Roku channel name, or "" to use the channel's own icon.
function cfMarkFor(name as String) as String
    n = LCase(name)
    if n = "max" or Instr(1, n, "hbo max") > 0 then return "hbomax"
    if Instr(1, n, "youtube tv") > 0 or Instr(1, n, "youtube kids") > 0 or Instr(1, n, "youtube music") > 0 then return ""
    keys = [["youtube", "youtube"], ["netflix", "netflix"], ["disney", "disneyplus"], ["prime video", "primevideo"], ["apple tv", "appletv"], ["tubi", "tubi"], ["spotify", "spotify"], ["plex", "plex"], ["crunchyroll", "crunchyroll"], ["fubo", "fubo"], ["stremio", "stremio"]]
    for each k in keys
        if Instr(1, n, k[0]) > 0 then return k[1]
    end for
    return ""
end function
