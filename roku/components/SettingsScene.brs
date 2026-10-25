' Rows: id, label. Values come from the Roku registry (shared with the screensaver).
sub init()
    m.rows = [
        { id: "clock", label: "Clock" },
        { id: "interval", label: "Change photo" },
        { id: "source", label: "Photos from" },
        { id: "key", label: "Unsplash access key" },
        { id: "user", label: "Unsplash username" },
        { id: "fetch", label: "Get Unsplash photos now" }
    ]
    m.focus = 0
    m.plate = m.top.findNode("plate")
    m.status = m.top.findNode("status")
    m.group = m.top.findNode("rows")
    m.labels = []
    for i = 0 to m.rows.Count() - 1
        row = CreateObject("roSGNode", "Group")
        row.translation = [0, i * 116]
        name = makeLabel(40, [24, 8])
        value = makeLabel(28, [24, 56])
        row.appendChild(name)
        row.appendChild(value)
        m.group.appendChild(row)
        m.labels.Push({ name: name, value: value })
    end for
    m.status.text = "Up and Down to choose, OK to change. Back saves and returns."
    render()
    m.top.setFocus(true)
end sub

function makeLabel(size as Integer, at as Object) as Object
    label = CreateObject("roSGNode", "Label")
    font = CreateObject("roSGNode", "Font")
    font.uri = "pkg:/fonts/Geist-Medium.ttf"
    font.size = size
    label.font = font
    label.translation = at
    label.width = 950
    return label
end function

function intervals() as Object
    return [["5", "Every 5 minutes"], ["10", "Every 10 minutes"], ["30", "Every 30 minutes"], ["60", "Every hour"]]
end function

' CleanFrame photos, Unsplash landscapes, all your collections, then each collection.
function sources() as Object
    list = [["cleanframe", "CleanFrame photos"], ["landscape", "Unsplash landscapes"]]
    cols = ParseJson(cfGet("unsplash_cols", "[]"))
    if cols <> invalid and cols.Count() > 0 then
        if cols.Count() > 1 then list.Push(["all", "All your Unsplash collections"])
        for each c in cols
            list.Push([c.id, c.title + " (" + c.total.ToStr() + ")"])
        end for
    end if
    return list
end function

function nameIn(list as Object, value as String) as String
    for each item in list
        if item[0] = value then return item[1]
    end for
    return list[0][1]
end function

function nextIn(list as Object, value as String) as String
    for i = 0 to list.Count() - 1
        if list[i][0] = value then return list[(i + 1) mod list.Count()][0]
    end for
    return list[0][0]
end function

function valueOf(id as String) as String
    if id = "clock" then
        if cfGet("clock", "12") = "24" then return "24-hour"
        return "12-hour"
    else if id = "interval" then
        return nameIn(intervals(), cfGet("interval", "10"))
    else if id = "source" then
        return nameIn(sources(), cfGet("source", "cleanframe"))
    else if id = "key" then
        k = cfGet("unsplash_key", "")
        if k = "" then return "Not set. Press OK and enter the Access Key from unsplash.com/oauth/applications"
        return "Saved (" + Chr(8230) + Right(k, 4) + ")"
    else if id = "user" then
        u = cfGet("unsplash_user", "")
        if u = "" then return "Optional, to use your own collections"
        return "@" + u
    else if id = "fetch" then
        cache = ParseJson(cfGet("unsplash_cache", "[]"))
        if cache = invalid or cache.Count() = 0 then return "No Unsplash photos saved yet"
        return cache.Count().ToStr() + " photos ready"
    end if
    return ""
end function

sub render()
    for i = 0 to m.rows.Count() - 1
        m.labels[i].name.text = m.rows[i].label
        m.labels[i].value.text = valueOf(m.rows[i].id)
        if i = m.focus then
            m.labels[i].name.color = "0x1C1C1AFF"
            m.labels[i].value.color = "0x1C1C1AB0"
        else
            m.labels[i].name.color = "0xF6F1E7FF"
            m.labels[i].value.color = "0xF6F1E7A0"
        end if
    end for
    m.plate.translation = [140, 200 + m.focus * 116]
end sub

sub choose(id as String)
    if id = "clock" then
        if cfGet("clock", "12") = "24" then cfSet("clock", "12") else cfSet("clock", "24")
    else if id = "interval" then
        cfSet("interval", nextIn(intervals(), cfGet("interval", "10")))
    else if id = "source" then
        cfSet("source", nextIn(sources(), cfGet("source", "cleanframe")))
        if cfGet("source", "cleanframe") <> "cleanframe" then runTask({ kind: "photos" }, "Getting photos" + Chr(8230))
    else if id = "key" then
        ask("Unsplash access key", "", "unsplash_key")
    else if id = "user" then
        ask("Unsplash username", cfGet("unsplash_user", ""), "unsplash_user")
    else if id = "fetch" then
        runTask({ kind: "photos" }, "Getting photos" + Chr(8230))
    end if
    render()
end sub

sub ask(title as String, current as String, key as String)
    d = CreateObject("roSGNode", "StandardKeyboardDialog")
    d.title = title
    d.text = current
    d.buttons = ["Save", "Cancel"]
    d.observeField("buttonSelected", "onDialog")
    m.askKey = key
    m.top.dialog = d
end sub

sub onDialog()
    d = m.top.dialog
    if d = invalid then return
    if d.buttonSelected = 0 then
        value = d.text.Trim()
        if m.askKey = "unsplash_user" then value = value.Replace("@", "")
        cfSet(m.askKey, value)
        if m.askKey = "unsplash_key" and value <> "" then cfSet("unsplash_cols", "[]")
        if m.askKey = "unsplash_user" and value <> "" then runTask({ kind: "collections" }, "Looking up your collections" + Chr(8230))
        if m.askKey = "unsplash_key" and value <> "" and cfGet("unsplash_user", "") <> "" then runTask({ kind: "collections" }, "Looking up your collections" + Chr(8230))
    end if
    d.close = true
    render()
end sub

sub runTask(command as Object, message as String)
    m.status.text = message
    m.task = CreateObject("roSGNode", "UnsplashTask")
    m.task.observeField("result", "onTask")
    m.task.command = command
    m.task.control = "RUN"
end sub

sub onTask()
    r = m.task.result
    if r.ok then
        if r.kind = "collections" then
            m.status.text = "Found " + r.count.ToStr() + " collections. Choose one under Photos from."
            if r.count > 0 and cfGet("source", "cleanframe") = "cleanframe" then cfSet("source", "all")
            if r.count = 1 then cfSet("source", sources()[2][0])
        else
            m.status.text = r.count.ToStr() + " Unsplash photos ready. The screensaver uses them next time it starts."
        end if
    else
        m.status.text = r.error
    end if
    render()
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "down" and m.focus < m.rows.Count() - 1 then
        m.focus = m.focus + 1
        render()
        return true
    else if key = "up" and m.focus > 0 then
        m.focus = m.focus - 1
        render()
        return true
    else if key = "OK" then
        choose(m.rows[m.focus].id)
        return true
    end if
    return false
end function
