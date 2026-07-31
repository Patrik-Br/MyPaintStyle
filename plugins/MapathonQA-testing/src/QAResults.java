package org.openstreetmap.josm.plugins.mapathonqa;

import java.util.*;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class QAResults {
    public int projectId;
    public String mapathonName = "";
    public String startTime, endTime;
    public java.util.Date since, until;

    // Set only for "Specific user(s)" reports (see RunFullQAAction/RunQAOnCurrentLayerAction).
    // targetUid <= 0 means this is a normal time-window (or demo) report.
    public int targetUid = 0;
    public String targetUsername = "";
    public boolean mapperMode = true;

    public int totalBuildings, totalHighways, totalNodes, totalWays, totalRelations;
    public int mapathonBuildings, mapathonHighways;
    public int totalMappers;
    public int issueMappers;

    public List<OsmPrimitive> nonYesBuildingTags = new ArrayList<>();
    public CheckOverlappingBuildingsAction.OverlapResult overlappingBuildings
        = new CheckOverlappingBuildingsAction.OverlapResult(new LinkedHashSet<>(), new LinkedHashSet<>(), 0, 0);
    public Set<OsmPrimitive> buildingsOnHighways  = new LinkedHashSet<>();
    public List<OsmPrimitive> nonOrthogonalBuildings = new ArrayList<>();
    public List<OsmPrimitive> buildingsWithLayerTag  = new ArrayList<>();
    public CheckBuildingsWithSharedNodesAction.SharedNodeResult buildingsWithSharedNodes
        = new CheckBuildingsWithSharedNodesAction.SharedNodeResult(0, new LinkedHashSet<>());
    public List<OsmPrimitive> untaggedObjects = new ArrayList<>();

    public Set<OsmPrimitive> allFlagged() {
        return allFlaggedUsing(overlappingBuildings.flaggedBuildings);
    }

    /**
     * Like allFlagged(), but counting BOTH sides of every overlapping-buildings pair instead of
     * just the one side that gets editor-selected. Editor selection only needs one side per pair;
     * "was this specific author involved at all" (used for the per-user report tally) needs both,
     * since which side is "flagged" is arbitrary once there's no time window to prioritize by.
     */
    public Set<OsmPrimitive> allFlaggedForUserTally() {
        return allFlaggedUsing(overlappingBuildings.allInvolvedBuildings);
    }

    private Set<OsmPrimitive> allFlaggedUsing(Set<OsmPrimitive> overlapSet) {
        Set<OsmPrimitive> all = new LinkedHashSet<>();
        for (OsmPrimitive p : nonYesBuildingTags)    if (p!=null) all.add(p);
        for (OsmPrimitive p : overlapSet) if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsOnHighways)    if (p!=null) all.add(p);
        for (OsmPrimitive p : nonOrthogonalBuildings) if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsWithLayerTag)  if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsWithSharedNodes.affectedBuildings) if (p!=null) all.add(p);
        for (OsmPrimitive p : untaggedObjects)        if (p!=null) all.add(p);
        return all;
    }

    public int totalIssueOverride = -1; // if >= 0, used instead of allFlagged().size() (for demo)
    public int totalIssues() { return totalIssueOverride >= 0 ? totalIssueOverride : allFlagged().size(); }

    // "Without issues" uses mapathon (in-window) counts when a time window was set, else totals
    public int mapathonFeatures() {
        return since != null ? (mapathonBuildings + mapathonHighways) : (totalBuildings + totalHighways);
    }

    public int cleanCount() {
        return Math.max(0, mapathonFeatures() - totalIssues());
    }

    /** How many of the given flagged objects were authored by the target user (0 if not a user report). */
    public int countForTargetUser(Collection<? extends OsmPrimitive> flagged) {
        if (targetUid <= 0) return 0;
        int count = 0;
        for (OsmPrimitive p : flagged) {
            if (p != null && p.getUser() != null && p.getUser().getId() == targetUid) count++;
        }
        return count;
    }
}
