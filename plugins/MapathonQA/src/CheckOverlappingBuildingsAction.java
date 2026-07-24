package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
public class CheckOverlappingBuildingsAction extends AbstractAction {

    /** Overlapping/contained buildings, plus how many of them are exact duplicates (see GeometryUtil.isExactDuplicate). */
    public static class OverlapResult {
        public Set<OsmPrimitive> flaggedBuildings;
        public int duplicateBuildingCount;
        public OverlapResult(Set<OsmPrimitive> flaggedBuildings, int duplicateBuildingCount) {
            this.flaggedBuildings = flaggedBuildings;
            this.duplicateBuildingCount = duplicateBuildingCount;
        }
        public int size() { return flaggedBuildings.size(); }
        public boolean isEmpty() { return flaggedBuildings.isEmpty(); }
    }

    public CheckOverlappingBuildingsAction() { super(I18n.tr("Check: Overlapping Buildings")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking overlapping buildings...");
        prog.setVisible(true);
        new SwingWorker<OverlapResult, Void>() {
            @Override protected OverlapResult doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    OverlapResult r = get();
                    if (r.isEmpty()) { JOptionPane.showMessageDialog(null, "No overlapping buildings found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(r.flaggedBuildings);
                    JOptionPane.showMessageDialog(null, I18n.tr("{0} building(s) overlap with another building.\n\nThey are now selected.", r.size()), "MapathonQA \u2013 Overlapping Buildings", JOptionPane.WARNING_MESSAGE);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static OverlapResult runOn(DataSet ds) { return runOn(ds, null, null); }
    public static OverlapResult runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static OverlapResult runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<Way> buildings = new ArrayList<>();
        for (Way w : ds.getWays()) if (w.isClosed()&&w.hasKey("building")&&w.getNodesCount()>=4) buildings.add(w);
        Set<OsmPrimitive> f = new LinkedHashSet<>();
        Set<OsmPrimitive> duplicates = new LinkedHashSet<>();
        int n=buildings.size();
        for (int i=0;i<n;i++) { Way a=buildings.get(i); for (int j=i+1;j<n;j++) { Way b=buildings.get(j);
            if (GeometryUtil.bboxDisjoint(a,b)) continue;
            boolean dup = GeometryUtil.isExactDuplicate(a,b);
            // a shared node normally just means two buildings sit side by side (e.g. row houses) -
            // not an overlap, unless the two ways are exact duplicates of each other.
            if (!dup && GeometryUtil.waysShareNode(a,b)) continue;
            if (dup || GeometryUtil.waysOverlap(a,b)) {
                OsmPrimitive flagged = null;
                if (GeometryUtil.isMappedDuring(a,since,until)) flagged=a; else if (GeometryUtil.isMappedDuring(b,since,until)) flagged=b;
                if (flagged!=null) { f.add(flagged); if (dup) duplicates.add(flagged); }
            }
        }}
        return new OverlapResult(f, duplicates.size());
    }
}
