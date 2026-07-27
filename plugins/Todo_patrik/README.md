# Todo – JOSM Plugin

Patrik's fork of the JOSM `todo` plugin (originally by Gnonthgol/AndrewBuck/bagage). Source was
recovered by decompiling `todo_patrik.jar` (Vineflower) since no source tree existed for this fork
before now; matched against `todo_anthaas.jar` (upstream) for reference.

Differences from upstream:
- Deleted/removed features stay in the list instead of disappearing.
- "Add+Zoom" button: add the current map selection to the list and zoom to the first item.
- Shortcuts are user-editable (registered via `JosmAction`/`Shortcut` instead of hardcoded key
  bindings).
- Right-click menu on the list gained a **Select** entry: selects every item currently highlighted
  in the todo list (multi-select with ctrl/shift-click) onto the map, across however many layers
  they belong to, without changing the map view. This is separate from the **Zoom** toolbar button,
  which only jumps to the first highlighted item.
