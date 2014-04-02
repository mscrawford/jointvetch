package jointvetch;

import java.util.*;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.distance.DistanceOp;
import com.vividsolutions.jts.operation.distance.GeometryLocation;

class DBSCAN
{
    private Bag geometries = new Bag(); // actual MasonGeometries
    private Bag database = new Bag(); // 'DBSCAN objects'
    private double epsilon; // distance
    private int minPts; // minimum cluster size
    private Bag clusters; // return bag of clusters

    private class DBObj
    {
        MasonGeometry geomPoint; 
        boolean visited; 
        boolean noise;
        boolean clustered;

        DBObj(MasonGeometry p) 
        {
            this.geomPoint = p;
            this.visited = false;
            this.noise = false; 
            this.clustered = false;
        }
    }

    Bag getPopulations(Bag masonGeometries, double epsilon, int minPts)
    {
        clusters = new Bag();
        this.geometries.addAll(masonGeometries);
        for (int j = 0, s = geometries.size(); j < s; j++) 
        {
            this.database.add(new DBObj( (MasonGeometry) geometries.get(j) ));
        }
        this.epsilon = epsilon;
        this.minPts = minPts;

        for (int i = 0, s = database.size(); i < s; i++)
        {
            DBObj current = (DBObj) database.get(i);
            if (current.visited == false)
            {
                current.visited = true;
                Bag neighborPts = new Bag(regionQuery(current));
                if (neighborPts.size() < minPts)
                {
                    current.noise = true;
                }
                else
                {
                    Bag cluster = new Bag();
                    expandCluster(current, cluster, neighborPts);
                }
            }
        }

        for (int i = 0, s = database.size(); i < s; i++)
        {
            DBObj cur = (DBObj) database.get(i);
            if (cur.noise == true)
            {
                Bag noiseBag = new Bag();
                noiseBag.add(cur.geomPoint);
                clusters.add(noiseBag);
            }
        }
        return clusters;
    }

    private void expandCluster(DBObj current, Bag cluster, Bag neighborPts) 
    {
        cluster.add(current.geomPoint);
        current.clustered = true;
        for (int k = 0, s = neighborPts.size(); k < s; k++) // for each point P' in NeighborPts
        {
            DBObj kth = (DBObj) neighborPts.get(k);
            if(kth.visited == false) // if P' is not visited
            {
                kth.visited = true; // mark P' as visited
                Bag neighborPtsPrime = regionQuery(kth); // NeighborPts' = regionQuery(P', eps)
                if(neighborPtsPrime.size() >= minPts) // if sizeof(NeighborPts') >= MinPts
                {
                    neighborPts.addAll(neighborPtsPrime); // NeighborPts = NeighborPts joined with NeighborPts'
                }
            }
            if(kth.clustered == false) // if P' is not yet member of any cluster
            {
                cluster.add(kth.geomPoint);
                kth.clustered = true;
                kth.noise = false; // if it was previously marked as noise
            } 
        }
        clusters.add(cluster);
    }

    private Bag regionQuery(DBObj p)
    { 
        Bag neighborPts = new Bag();
        for (int i = 0, s = database.size(); i < s; i++)
        {
            DBObj other = (DBObj) database.get(i);
            if (DistanceOp.distance(other.geomPoint.getGeometry(), p.geomPoint.getGeometry()) <= epsilon)
            {
                neighborPts.add(database.get(i));
            }
        }
        return neighborPts;
    } 
}
