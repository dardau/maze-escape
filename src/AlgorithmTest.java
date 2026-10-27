import java.util.Arrays;
import java.util.Random;

/** Independent small-graph oracles and a million-cell stack-safety check. */
public class AlgorithmTest {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        testSingleCell();
        testMazes();
        testSlides();
        testDeepGraphs();
        System.out.println("All algorithm checks passed: singleton, 100 mazes, 200 slide graphs, million-cell DFS, deep SCC.");
    }

    private static void testSingleCell() {
        Input.Walls w = new Input.Walls(1, 1, new int[0], new int[0], new int[0]);
        Escape.Result r = Escape.escape(w, Maze.breakWalls(w));
        require(Arrays.equals(r.path(), new int[]{0}) && r.visited() == 1 && r.deadEnds() == 0, "Singleton escape");
        require(Maze.cheapestMaze(w).length == 0 && Maze.primCost(w) == 0, "Singleton MST");
        Input.Slides s = new Input.Slides(1, new int[0], new int[0], new int[0]);
        require(Arrays.equals(Zones.routes(s, Zones.kosaraju(s), 1), new long[]{1, 0}), "Singleton routes");
    }

    private static void testMazes() {
        for (int seed = 0; seed < 100; seed++) {
            Input.Walls w = Generator.generate(2, 3, seed);
            int[] broken = Maze.breakWalls(w);
            require(broken.length == 5, "Spanning tree edge count");
            int[][] g = Graphs.open(w, broken);
            int[] parent = new int[6], visits = {0};
            Arrays.fill(parent, -1);
            referenceDfs(0, 5, w.cols(), g, new boolean[6], parent, visits);
            int length = 1;
            for (int u = 5; u != 0; u = parent[u]) length++;
            int[] path = new int[length];
            for (int u = 5, i = length - 1; i >= 0; i--, u = parent[u]) path[i] = u;
            Escape.Result r = Escape.escape(w, broken);
            require(Arrays.equals(path, r.path()) && r.visited() == visits[0], "DFS order/path");
            int leaves = 0;
            for (int[] neighbours : g) if (neighbours.length == 1) leaves++;
            require(r.deadEnds() == leaves, "Dead ends");
            long minimum = Long.MAX_VALUE;
            // Exhaustively examine every five-wall subset, without using our DSU.
            for (int mask = 0; mask < (1 << w.count()); mask++) {
                if (Integer.bitCount(mask) != 5) continue;
                boolean[][] reach = new boolean[6][6];
                long cost = 0;
                for (int i = 0; i < 6; i++) reach[i][i] = true;
                for (int k = 0; k < w.count(); k++) if ((mask & (1 << k)) != 0) {
                    reach[w.a()[k]][w.b()[k]] = reach[w.b()[k]][w.a()[k]] = true;
                    cost += w.cost()[k];
                }
                closure(reach);
                boolean connected = true;
                for (int i = 0; i < 6; i++) connected &= reach[0][i];
                if (connected) minimum = Math.min(minimum, cost);
            }
            long kruskal = 0;
            for (int k : Maze.cheapestMaze(w)) kruskal += w.cost()[k];
            require(kruskal == minimum && Maze.primCost(w) == minimum, "MST exhaustive oracle");
        }
    }

    // Recursion is used only in this six-cell test oracle, never in production algorithms.
    private static boolean referenceDfs(int u, int target, int cols, int[][] g,
                                        boolean[] seen, int[] parent, int[] visits) {
        seen[u] = true; visits[0]++;
        if (u == target) return true;
        for (int v : new int[]{u - cols, u + 1, u + cols, u - 1}) {
            if (v < 0 || v >= g.length || seen[v]) continue;
            for (int next : g[u]) if (next == v) {
                parent[v] = u;
                if (referenceDfs(v, target, cols, g, seen, parent, visits)) return true;
            }
        }
        return false;
    }

    private static void closure(boolean[][] r) {
        for (int k = 0; k < r.length; k++)
            for (int i = 0; i < r.length; i++)
                for (int j = 0; j < r.length; j++) r[i][j] |= r[i][k] && r[k][j];
    }

    private static void testSlides() {
        Random random = new Random(250304);
        for (int trial = 0; trial < 200; trial++) {
            int n = 2 + random.nextInt(8), m = n - 1 + random.nextInt(n * n);
            int[] from = new int[m], to = new int[m], time = new int[m];
            boolean[][] reach = new boolean[n][n];
            for (int i = 0; i < n; i++) reach[i][i] = true;
            for (int k = 0; k < m; k++) {
                from[k] = k < n - 1 ? k : random.nextInt(n);
                to[k] = k < n - 1 ? k + 1 : random.nextInt(n - 1);
                if (k >= n - 1 && to[k] >= from[k]) to[k]++;
                time[k] = 1 + random.nextInt(50);
                reach[from[k]][to[k]] = true;
            }
            Input.Slides s = new Input.Slides(n, from, to, time);
            closure(reach);
            int[] zones = Zones.kosaraju(s);
            for (int u = 0; u < n; u++) for (int v = 0; v < n; v++)
                require((zones[u] == zones[v]) == (reach[u][v] && reach[v][u]), "SCC reachability oracle");
            int count = Arrays.stream(zones).max().orElse(-1) + 1;
            long inf = Long.MAX_VALUE / 4;
            long[][] dist = new long[count][count];
            boolean[][] edges = new boolean[count][count];
            for (int i = 0; i < count; i++) { Arrays.fill(dist[i], inf); dist[i][i] = 0; }
            for (int k = 0; k < m; k++) {
                int a = zones[from[k]], b = zones[to[k]];
                if (a == b) continue;
                require(a < b, "Topological numbering");
                edges[a][b] = true;
                dist[a][b] = Math.min(dist[a][b], time[k]);
            }
            for (int k = 0; k < count; k++) for (int i = 0; i < count; i++)
                for (int j = 0; j < count; j++) dist[i][j] = Math.min(dist[i][j], dist[i][k] + dist[k][j]);
            int longest = 0;
            for (int i = 0; i < count; i++) longest = Math.max(longest, longestFrom(i, edges));
            long[] result = Zones.routes(s, zones, count);
            require(result[0] == longest && result[1] == dist[zones[0]][zones[n - 1]], "Zone routes oracle");
        }
        // The longest chain need not begin in the start zone; parallel edges use the minimum.
        Input.Slides s = new Input.Slides(6, new int[]{0,0,1,2,3}, new int[]{5,5,2,3,4}, new int[]{20,7,1,1,1});
        int[] z = Zones.kosaraju(s);
        require(Arrays.equals(Zones.routes(s,z,6), new long[]{4,7}), "Global stages and parallel slide minimum");
        Input.Slides cycle = new Input.Slides(3,new int[]{0,1,2},new int[]{1,2,0},new int[]{5,6,7});
        require(Arrays.equals(Zones.routes(cycle,Zones.kosaraju(cycle),1),new long[]{1,0}), "Movement inside a zone is free");
    }

    private static int longestFrom(int u, boolean[][] edges) {
        int best = 1;
        for (int v = 0; v < edges.length; v++) if (edges[u][v]) best = Math.max(best,1+longestFrom(v,edges));
        return best;
    }

    private static void testDeepGraphs() {
        int n = 1_000_000;
        int[] a = new int[n - 1], b = new int[n - 1], cost = new int[n - 1], broken = new int[n - 1];
        for (int i = 0; i < n - 1; i++) { a[i] = i; b[i] = i+1; cost[i] = 1; broken[i] = i; }
        Escape.Result r = Escape.escape(new Input.Walls(1,n,a,b,cost),broken);
        require(r.path().length == n && r.visited() == n && r.deadEnds() == 2, "Million-cell corridor");
        int[] zones = Zones.kosaraju(new Input.Slides(n,a,b,cost));
        for (int i = 0; i < n; i++) require(zones[i] == i, "Deep DAG SCC order");
        Input.Walls large = Generator.generate(1000,1000,250304);
        Escape.Result grid = Escape.escape(large,Maze.breakWalls(large));
        require(grid.path()[0] == 0 && grid.path()[grid.path().length-1] == n-1, "1000x1000 escape");
    }
}
