import java.io.*;
import java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

class Rule {
//    long sourceIPstart, sourceIPend;
//    long destIPstart, destIPend;
//    int sourcePortstart, destPortstart;
//    int sourcePortend, destPortend;
//    int proto, proto_mask;
    //以上是intervals中每个元素表示的含义
    int id;
    Interval [] intervals;
    Rule()
    {
        this.intervals = new Interval[5];
        for(int i = 0; i<this.intervals.length; i++)
            this.intervals[i] = new Interval(0, 0);
    }

    boolean match(long[] Component)
    {
        for(int i = 0; i < 5; i++)
        {
            if(Component[i] < this.intervals[i].start || Component[i] > this.intervals[i].end)
            {
                return false;
            }
        }
        return true;
    }
}

class Interval{
    long start, end;
    Interval(long start, long end){
        this.start = start;
        this.end = end;
    }
    /// 求两个集合的交集
    void intersection(Interval other){
        if(this.start < other.start)
            this.start = other.start;
        if(this.end > other.end)
            this.end = other.end;
    }

    long getRangelength(){
        return end - start + 1;
    }
    /*Set没有实现去重功能，原因是：
    没有重写 equals 和 hashCode，因此它继承自 Object 的默认实现：
    equals()：比较引用地址（不是值）    hashCode()：基于对象内存地址（每次 new 都不同）
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                // 1. 自反性：自己等于自己
        if (o == null || getClass() != o.getClass()) return false;// 2. 非空、类型一致
        Interval interval = (Interval) o;           // 3. 类型安全转换
        return start == interval.start && end == interval.end;
    }

    @Override
    public int hashCode() {
        //如果两个对象 equals() 返回 true，它们的 hashCode() 必须相等
        return Objects.hash(start, end);
    }
    /// 判断两个区间是否具有公共部分
    boolean isOverlap(Interval interval){
        if(interval.start >= this.start && interval.start <= this.end)
            return true;
        else if (interval.end >= this.start && interval.end <= this.end)
            return true;
        else if (interval.start < this.start && interval.end > this.end)
            return true;
        return false;
    }
}

class Node{
    int id;
    int spfac = 4;
    int [] dims;
    /// 各字段分支数
    int [] cutNum;
    List<Node> children;
    List<Rule> rules;
    Interval[] ranges;
    Node(Interval[] ranges)
    {
        this.children = new ArrayList<>();
        this.rules = new ArrayList<>();
        this.cutNum = new int[5];
        dims = new int[5];
        for(int i=0; i<5; i++)
        {
            dims[i] = i;
        }
        this.ranges = ranges;
    }

    public boolean isleaf()
    {
        return this.children.isEmpty();
    }
    /// 获取每个字段上的不同取值个数
    public int[] getvalueNum()
    {
        int[] list = new int[5];
        //int rulenum = this.rules.size();

        Set<Interval> intervalSet = new HashSet<Interval>();
        for(int i=0; i<5; i++)
        {
            for(Rule rule : this.rules)
            {
                intervalSet.add(rule.intervals[i]);
            }
            list[i] = intervalSet.size();
            intervalSet.clear();
        }
        return list;
    }
    /// 判断结点与规则之间的包含关系
    public boolean overlapsInAllDimensions(Rule rule)
    {
        boolean flag = true;
        for(int i=0; i<5; i++)
        {
            if(!this.ranges[i].isOverlap(rule.intervals[i]))
                flag = false;
        }
        return flag;
    }
    /// 确定每个字段的分支数
    public int[] getSplitNum(boolean[] isCut)
    {
        int l = isCut.length;
        int[] splitNum = new int[l];
        int totalsplit = 1;
        /// 确定第i个字段的最佳分支数
        for(int i=0; i<l; i++)
        {
            if(!isCut[i])//该字段未被挑选
            {
                splitNum[i] = 1;
            }
            else
            {
                int lastmeancount = rules.size();
                int lastmaxcount = rules.size();
                int lastnullLeafnum = 0;
                int cutNum = 1;
                while(true)
                {
                    cutNum = cutNum * 2;
                    int nullLeafnum = 0;
                    int maxcount = 0, meancount;
                    List<Node> tempchildren = new ArrayList<>();
                    long rangelength = ranges[i].getRangelength();
                    //确定每个叶子结点的范围
                    long childRangeLength = rangelength / cutNum;
                    //创建叶子结点
                    for(int j=0; j<cutNum; j++)
                    {
                        Interval[] intervals = new Interval[5];
                        for(int k = 0; k < 5; k++)
                        {
                            intervals[k] = new Interval(this.ranges[k].start, this.ranges[k].end);
                        }
                        intervals[i].start = this.ranges[i].start + childRangeLength * j;
                        intervals[i].end = intervals[i].start + childRangeLength - 1;
                        if(j == cutNum - 1)
                        {
                            intervals[i].end = this.ranges[i].end;
                        }
                        Node child = new Node(intervals);
                        //用叶子结点对规则进行划分
                        for(Rule rule : this.rules)
                        {
                            if(rule.intervals[i].isOverlap(child.ranges[i]))
                            {
                                child.rules.add(rule);
                            }
                        }
                        tempchildren.add(child);
                    }
                    //计算划分停止条件
                    int sumrulenum = 0;
                    for(Node child : tempchildren)
                    {
                        sumrulenum += child.rules.size();
                        if(child.rules.isEmpty()) {
                            nullLeafnum++;
                        }else if (child.rules.size() > maxcount) {
                            maxcount = child.rules.size();
                        }
                    }
                    meancount = sumrulenum / (tempchildren.size() - nullLeafnum);
                    //论文中停止分割的判断条件
                    if(nullLeafnum - lastnullLeafnum > 5 || Math.abs(meancount - lastmeancount) < 0.1 * lastmeancount
                     || Math.abs(maxcount - lastmaxcount) < 0.1 * lastmaxcount || childRangeLength <= 1
                     || totalsplit * cutNum > this.spfac * Math.sqrt(this.rules.size()))
                    {
                        break;
                    }
                    else {
                        lastmeancount = meancount;
                        lastmaxcount = maxcount;
                        lastnullLeafnum = nullLeafnum;
                    }

                }
                cutNum = cutNum / 2;
                splitNum[i] = cutNum;
                totalsplit = totalsplit * cutNum;
            }
        }
        return splitNum;
    }
    /// 启发式优化3：区域压缩
    public void getRangeOfRules()
    {
        Interval[] intervals = new Interval[5];
        long[] start = new long[5];
        long[] end = new long[5];
        for(int i=0; i<5; i++)
        {
            start[i] = Long.MAX_VALUE;
            end[i] = Long.MIN_VALUE;
        }
//        for (int i=0; i<5; i++)
//        {
//
//            intervals[i] = new Interval(rules.get(0).intervals[i].start, rules.get(0).intervals[i].end);
//        }
        for(Rule rule : this.rules)
        {
            for(int i = 0; i<5; i++)
            {
                if(rule.intervals[i].start < start[i])
                    start[i] = rule.intervals[i].start;
                if(rule.intervals[i].end > end[i])
                    end[i] = rule.intervals[i].end;
            }
        }
        for(int i=0; i<5; i++)
        {
            //ranges[i].intersection(intervals[i]);
            if(ranges[i].start < start[i])
                ranges[i].start = start[i];
            if(ranges[i].end > end[i])
                ranges[i].end = end[i];
        }
    }
    /// 通过每个字段的区间下标获得子结点
    public int getChildIndex(int[] ind)
    {
        int index = 0;
        for (int i=0; i<5; i++)
        {
            int tmp = ind[i];
            for(int j = i + 1; j<5; j++)
            {
                tmp *= this.cutNum[j];
            }
            index += tmp;
        }
        return index;
    }
}

class HyperCutsTree{
    int threshold = 16;
    int dim_num = 5;
    Node root;
    List<Rule> rules = new ArrayList<>();

    HyperCutsTree(List<Rule> rule)
    {
        this.rules = rule;
        Interval [] intervals = new Interval[5];
        //为每一个字段设置范围
        for(int i=0; i<5; i++)
        {
            intervals[i] = new Interval(0, 0);
        }
        intervals[0].end = intervals[1].end = (1L << 32) - 1;
        intervals[2].end = intervals[3].end = 65535;
        intervals[4].end = 255;
        root = new Node(intervals);
        root.rules = rule;
        root.getRangeOfRules();
    }
    /// 根据子结点的编号计算各字段范围的下标
    public int[] getRangeIndex(int index, int[] cutNum)
    {
        int[] rangeIndex = new int[5];
        int temp = index;
        for(int i = 4; i >= 0; i--)
        {
            rangeIndex[i] = temp % cutNum[i];
            temp /= cutNum[i];
        }
        return rangeIndex;
    }

    public void build_HyperCutsTree()
    {
        List<Node> nodeList = new ArrayList<Node>();
        nodeList.add(root);
        while(!nodeList.isEmpty())
        {
            try{
                Node node = nodeList.remove(0);
                //System.out.println("the number of rules in this node is " + node.rules.size());
                if (node.rules.size() <= threshold)
                {
                    //停止划分
                    continue;
                }
                int rulenum = node.rules.size();
                //挑选划分字段
                int[] dims = node.getvalueNum();
                boolean[] choice = new boolean[dim_num];
                Arrays.fill(choice, false);
                int sum = 0;
                for(int d : dims)
                    sum += d;
                int meanvalue = sum / dim_num;
                for(int i = 0; i < dim_num; i++)
                {
                    if(dims[i] >= meanvalue)
                        choice[i] = true;
                }
                //System.out.println("The mean value of each field is " + meanvalue);
                //确定分支数和划分后每个字段的范围
                int[] splitNum = node.getSplitNum(choice);
                node.cutNum = splitNum;
                long[] dimRangelen = new long[5];
                for(int i=0; i<5; i++)
                {
                    dimRangelen[i] = node.ranges[i].getRangelength() / splitNum[i];
                }
//                System.out.println("The best split num of each dimension is: ");
//                for(int split : splitNum)
//                {
//                    System.out.println(split);
//                }
                //使用分支结果构建决策树
                List<Integer> cutdim = new ArrayList<>();
                int leafnum = 0, tmp = 0;
                for(int i = 0; i < splitNum.length; i++)
                {
                    if(splitNum[i] != 1) {
                        cutdim.add(i);
                    }else {
                        tmp++;
                    }
                }
                if(tmp == splitNum.length)
                {
                    //没有挑选出划分字段
                    continue;
                }
                int totalsplit = 1;
                for(int split : splitNum)
                {
                    totalsplit = totalsplit * split;
                }
                //System.out.println("the child number of this node is " + totalsplit);
                for(int i = 0; i < totalsplit; i++)
                {
                    int[] rangeIndex = getRangeIndex(i, splitNum);
                    Interval[] intervals = new Interval[5];
                    for (int j = 0; j < 5; j++)
                    {
                        long start = node.ranges[j].start + rangeIndex[j] * dimRangelen[j];
                        long end = start + dimRangelen[j] - 1;
                        if(rangeIndex[j] == splitNum[j] - 1)
                        {
                            //修正最后一个区间的范围
                            end = node.ranges[j].end;
                        }
                        intervals[j] = new Interval(start, end);
                    }
                    Node child = new Node(intervals);
                    //将规则集分配到子结点
                    for(Rule rule : node.rules)
                    {
                        if(child.overlapsInAllDimensions(rule))
                            child.rules.add(rule);
                    }
                    child.getRangeOfRules();
                    //更新结点集合
                    nodeList.add(child);
                    node.children.add(child);
                }

            }
            catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

    }
}

public class Main {
    public static List<Rule> load_rules_from_file(String filename) {
        List<Rule> rule = new ArrayList<>();
        int line_count = 0;
        String[] IPadd;
        int mask;
        long netmask, regionlen;
        String[] port;
        //读取文件，识别出每一条规则
        try{
            LineNumberReader lnr = new LineNumberReader(new FileReader(filename));
            lnr.skip(Long.MAX_VALUE);
            line_count = lnr.getLineNumber();
            System.out.println("Rule count:" +line_count);
            int index = 0;
            lnr.close();

            BufferedReader buffer = new BufferedReader(new FileReader(filename));
            String line = buffer.readLine();
            while (line != null)
            {
                String[] Component = line.split("\t");

                try{
                    Rule r = new Rule();
                    r.id = index;
                    IPadd = Component[0].split("/");
                    byte[] sourceIP = InetAddress.getByName(IPadd[0]).getAddress();
                    r.intervals[0].start = (Byte.toUnsignedLong(sourceIP[0]) << 24) + (Byte.toUnsignedLong(sourceIP[1]) << 16)
                            + (Byte.toUnsignedLong(sourceIP[2]) << 8) + Byte.toUnsignedLong(sourceIP[3]);
                    mask = Integer.parseInt(IPadd[1]);
                    //求子网掩码
                    netmask = (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
                    r.intervals[0].start = r.intervals[0].start & netmask;
                    regionlen = (1L << (32 - mask)) - 1;
                    r.intervals[0].end = r.intervals[0].start + regionlen;
//                    System.out.println("Source IP start:" +r.sourceIPstart);
//                    System.out.println("Source IP end:" +r.sourceIPend);

                    IPadd = Component[1].split("/");
                    byte[] destIP = InetAddress.getByName(IPadd[0]).getAddress();
                    r.intervals[1].start = (Byte.toUnsignedLong(destIP[0]) << 24) + (Byte.toUnsignedLong(destIP[1]) << 16)
                            + (Byte.toUnsignedLong(destIP[2]) << 8) + Byte.toUnsignedLong(destIP[3]);
                    mask = Integer.parseInt(IPadd[1]);
                    netmask = (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;
                    r.intervals[1].start = r.intervals[1].start & netmask;
                    regionlen = (1L << (32 - mask)) - 1;
                    r.intervals[1].end = r.intervals[1].start + regionlen ;

                    port = Component[2].split(":");
                    r.intervals[2].start = Integer.parseInt(port[0].trim());
                    r.intervals[2].end = Integer.parseInt(port[1].trim());
                    port = Component[3].split(":");
                    r.intervals[3].start = Integer.parseInt(port[0].trim());
                    r.intervals[3].end = Integer.parseInt(port[1].trim());

                    IPadd = Component[4].split("/");
                    //读取十六进制数，协议使用精确匹配
                    int proto = Integer.parseInt(IPadd[0].trim().substring(2), 16);
                    r.intervals[4] = new Interval(proto, proto); // 范围 [6,6]

                    rule.add(r);
                    index++;
                }
                catch (UnknownHostException e)
                {
                    System.out.println("Unknown host" + e.getMessage());
                }
                line = buffer.readLine();
            }
        }catch (IOException e)
        {
            System.out.println("Error reading rules from file");
        }
        return rule;
    }

    public static void lookup(String filename, HyperCutsTree tree)
    {
        int line_count = 0, matchnum = 0;
        try{
            LineNumberReader lrn = new LineNumberReader(new FileReader(filename));
            lrn.skip(Long.MAX_VALUE);
            line_count = lrn.getLineNumber();
            System.out.println("Packet count:" +line_count);
            lrn.close();
            long starttime = System.nanoTime();

            BufferedReader buffer = new BufferedReader(new FileReader(filename));
            String line = buffer.readLine();
            while(line != null)
            {
                String[] Component = line.split("\t");
                try {
                    int len = Component.length;
                    long[] Component1 = new long[len];
                    for (int i = 0; i < len; i++) {
                        Component1[i] = Long.parseLong(Component[i].trim());
                    }
                    Node node = tree.root;
                    while(!node.isleaf())
                    {
                        int[] ind = new int[5];
                        //计算落入子结点的下标
                        for(int i = 0; i < 5; i++)
                        {
                            long childrange = (node.ranges[i].end - node.ranges[i].start + 1) / node.cutNum[i];
                            int index = (int) ((Component1[i] - node.ranges[i].start) / childrange);
                            if(Component1[i] - node.ranges[i].start >= childrange * (node.cutNum[i] - 1))
                            {
                                index = node.cutNum[i] - 1;
                            }
                            ind[i] = index;
                        }
                        int childindex = node.getChildIndex(ind);
                        node = node.children.get(childindex);
                    }
                    int res = -1;
                    for(Rule r : node.rules)
                    {
                        if(r.match(Component1))
                        {
                            res = r.id;
                            break;
                        }
                    }
                    //System.out.println("The match result of this packet is" + res);
                    if(res == Component1[Component1.length - 1])
                    {
                        matchnum++;
                    }
                    else
                    {
                        System.out.println(line + " " + res);
                    }
                    line = buffer.readLine();
                }
                catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            long endtime = System.nanoTime();
            long elapsedtime = endtime - starttime;
            System.out.println("Match count:" + matchnum + "/" + line_count);
            System.out.println("The time spent for looking up is :" + elapsedtime + "ns");
        }catch (IOException e)
        {
            System.out.println("Error reading data from test file");
        }
    }

    public static void main(String[] args) {
        String filename = "Filter_1K_acl4seed.txt";
        List<Rule> rule = load_rules_from_file(filename);
        long starttime = System.currentTimeMillis();
        HyperCutsTree tree = new HyperCutsTree(rule);
        tree.build_HyperCutsTree();
        long endtime = System.currentTimeMillis();
        long elapsed = endtime - starttime;
        System.out.println("The time cost for building the hypercut tree is "
                + elapsed + "ms");
        String testfilename = "Filter_1K_acl4seed_trace.txt";
        lookup(testfilename, tree);
        System.out.println("Hello, World!");
    }
}