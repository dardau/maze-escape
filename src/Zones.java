/**
 * Missions 4 and 5. The slides: a directed graph. Cells are 0 .. n-1, slide i goes from s.from()[i] to s.to()[i]
 * and takes s.time()[i]. There are no slides from a cell to itself, and the exit can always be reached from the start.
 *
 * Ready for you: Graphs.slidesFrom(s)[u] is the list of the slides that leave cell u, and Graphs.slidesInto(s)[v] is the list
 * of the slides that enter cell v (reversed graph). The lists hold slide numbers i; the cell at the other end is s.to()[i]
 * or s.from()[i].
 */
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Zones {

    /**
     * Mission 4. Kosaraju. Returns zoneOf: zoneOf[cell] = number of the zone, numbers 0 .. zones-1.
     * Number the zones in the order in which the second pass finds them. That order is a topological order
     * of the zone graph: every slide between two zones goes from a smaller number to a larger number.
     *
     * Plan: 1) DFS on the slides, write the cells in the order in which they are finished (use a stack, not recursion);
     * 2) go through the cells from the last finished to the first; if a cell has no zone yet, DFS from it on the reversed
     * slides (slidesInto) and give every cell it reaches the next zone number.
     * Is DAG in Main is 1 when the number of zones equals the number of cells (no cycle), so you do not need a separate check.
     */
    public static int[] kosaraju(Input.Slides s) {
        int n = s.n();
        int[][] outgoing = Graphs.slidesFrom(s);
        int[][] incoming = Graphs.slidesInto(s);
        boolean[] seen = new boolean[n];
        int[] stack = new int[n], nextEdge = new int[n], finish = new int[n];
        int finished = 0;
        for (int start = 0; start < n; start++) {
            if (seen[start]) continue;
            int top = 0;
            stack[0] = start;
            seen[start] = true;
            while (top >= 0) {
                int u = stack[top];
                if (nextEdge[u] == outgoing[u].length) {
                    finish[finished++] = u;
                    top--;
                } else {
                    int v = s.to()[outgoing[u][nextEdge[u]++]];
                    if (!seen[v]) { seen[v] = true; stack[++top] = v; }
                }
            }
        }
        int[] zoneOf = new int[n];
        Arrays.fill(zoneOf, -1);
        int zones = 0;
        for (int i = finished - 1; i >= 0; i--) {
            int start = finish[i];
            if (zoneOf[start] != -1) continue;
            int size = 0;
            stack[size++] = start;
            zoneOf[start] = zones;
            while (size > 0) {
                int u = stack[--size];
                for (int edge : incoming[u]) {
                    int v = s.from()[edge];
                    if (zoneOf[v] == -1) {
                        zoneOf[v] = zones;
                        stack[size++] = v;
                    }
                }
            }
            zones++;
        }
        return zoneOf;
    }

    /**
     * Mission 5. The graph of zones: the edge X to Y costs the cheapest slide from X to Y, walking inside a zone is free.
     * Go through the zones in the order of their numbers (no priority queue). Returns two numbers:
     * [0] flow stages: the number of zones on the longest chain of zones;
     * [1] the cost of the cheapest route from the zone of cell 0 to the zone of the last cell.
     */
    public static long[] routes(Input.Slides s, int[] zoneOf, int zoneCount) {
        if (zoneCount == 0) return new long[]{0, 0};
        // Merge parallel slides: only the cheapest slide between two zones matters.
        java.util.List<Map<Integer, Integer>> graph = new java.util.ArrayList<>(zoneCount);
        for (int z = 0; z < zoneCount; z++) graph.add(new HashMap<>());
        for (int i = 0; i < s.count(); i++) {
            int from = zoneOf[s.from()[i]], to = zoneOf[s.to()[i]];
            if (from != to) graph.get(from).merge(to, s.time()[i], Math::min);
        }
        long[] distance = new long[zoneCount];
        Arrays.fill(distance, Long.MAX_VALUE);
        distance[zoneOf[0]] = 0;
        int[] stages = new int[zoneCount];
        Arrays.fill(stages, 1); // A chain may start in any zone, including an isolated one.
        int longest = 1;
        for (int from = 0; from < zoneCount; from++) {
            longest = Math.max(longest, stages[from]);
            for (Map.Entry<Integer, Integer> edge : graph.get(from).entrySet()) {
                int to = edge.getKey();
                if (to <= from) throw new IllegalArgumentException("Zones must be in topological order");
                stages[to] = Math.max(stages[to], stages[from] + 1);
                if (distance[from] != Long.MAX_VALUE) {
                    distance[to] = Math.min(distance[to], distance[from] + edge.getValue());
                }
            }
        }
        return new long[]{longest, distance[zoneOf[s.n() - 1]]};
    }
}
