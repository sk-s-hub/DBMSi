package cmdline;

import bitmap.AddFileEntryException;
import bitmap.BitMapScanException;
import bitmap.ConstructPageException;
import bitmap.GetFileEntryException;
import bitmap.PinPageException;
import bitmap.UnpinPageException;
import columnar.*;
import diskmgr.DiskMgrException;
import global.AttrOperator;
import global.AttrType;
import global.IndexType;
import global.SystemDefs;
import heap.HFBufMgrException;
import heap.HFDiskMgrException;
import heap.HFException;
import heap.InvalidSlotNumberException;
import heap.Tuple;
import iterator.BitmapScan;
import iterator.BtreeScan;
import iterator.ColumnScan;
import iterator.ColumnarFileScan;
import iterator.ColumnarFileScanException;
import iterator.ColumnarIndexScan;
import iterator.CondExpr;
import iterator.FldSpec;
import iterator.IndexException;
import iterator.Iterator;
import iterator.NestedLoopJoins;
import iterator.RelSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ColumnarNestedLoopJoin {

    private static String columnDBName;
    private static String outerFile;
    private static String innerFile;
    private static ArrayList<String> targetColumnNames;
    private static StringBuffer outerConst = new StringBuffer();
    private static StringBuffer innerConst = new StringBuffer();
    private static StringBuffer joinConst = new StringBuffer();
    private static int numBuf;
    private static IndexType indexType;
    private static AttrType[] in1;
    private static AttrType[] in2;
    private static CondExpr[] outerCondExpr;
    private static CondExpr[] innerCondExpr;
    private static CondExpr[] joinCondExpr;
    private static CondExpr joinExpr;
//    private static CondExpr[] joinCondExpr;

    public static void main(String argv[]) throws Exception {
        initFromArgs(argv);
    }

    /*
     * Function to parse the arguments
     */
    private static void initFromArgs(String argv[]) throws Exception {
        int lengthOfArgv = argv.length;

        targetColumnNames = new ArrayList<String>();
        columnDBName = argv[0];
        outerFile = argv[1];
        innerFile = argv[2];

        int i = 0;
        if (argv[3].equals("[")) {
            i = 4;
            while (!argv[i].equals("]")) {
                if (argv[i].equals("(") || argv[i].equals(")"))
                    i++;
                else {
                    outerConst.append(argv[i] + " ");
                    i++;
                }
            }
        }
        if (argv[i + 1].equals("[")) {
            i = i + 2;
            while (!argv[i].equals("]")) {
                if (argv[i].equals("(") || argv[i].equals(")"))
                    i++;
                else {
                    innerConst.append(argv[i] + " ");
                    i++;
                }
            }
        }
        if (argv[i + 1].equals("[")) {
            i = i + 2;
            while (!argv[i].equals("]")) {
                joinConst.append(argv[i] + " ");
                i++;
            }
        }

        indexType = CommandLineHelper.getIndexType(argv[i + 1]);
        i = i + 1;
        for (i = i + 2; i <= lengthOfArgv - 3; i++) {
            targetColumnNames.add(argv[i]);
        }

        outerConst.deleteCharAt(outerConst.length() - 1);
        innerConst.deleteCharAt(innerConst.length() - 1);
        joinConst.deleteCharAt(joinConst.length() - 1);
        numBuf = Integer.parseInt(argv[lengthOfArgv - 1]);
        SystemDefs systemDefs = new SystemDefs(columnDBName, 0, numBuf, "LRU");
        setUpJoin();
    }

    private static void setUpJoin() throws ColumnarFileScanException,
        BitMapScanException, IndexException, Exception {
        ColumnarFile columnarFile = new ColumnarFile(outerFile);

        ArrayList<FldSpec> proj1 = new ArrayList<FldSpec>();
        ArrayList<FldSpec> proj2 = new ArrayList<FldSpec>();

        in1 = columnarFile.getColumnarHeader().getColumns();

        columnarFile = new ColumnarFile(innerFile);
        in2 = columnarFile.getColumnarHeader().getColumns();

        outerCondExpr = CommandLineHelper.parseToCondExpr(outerFile,
            outerConst);
        innerCondExpr = CommandLineHelper.parseToCondExpr(innerFile,
            innerConst);
        joinExpr = CommandLineHelper.parseJoinConstr(outerFile, innerFile, joinConst);

        for (int i = 0; i < targetColumnNames.size(); i++) {
            for (int j = 0; j < in1.length; j++) {
                if (targetColumnNames.get(i).equals(in1[j].getAttrName())) {

                    FldSpec fldSpec = new FldSpec(new RelSpec(RelSpec.outer),
                        in1[j].getColumnId());
                    proj1.add(fldSpec);
                }
            }
        }
        for (int i = 0; i < targetColumnNames.size(); i++) {
            for (int j = 0; j < in1.length; j++) {
                if (targetColumnNames.get(i).equals(in2[j].getAttrName())) {

                    FldSpec fldSpec = new FldSpec(new RelSpec(RelSpec.innerRel),
                        in1[j].getColumnId());
                    proj2.add(fldSpec);
                }
            }
        }

        joinCondExpr = new CondExpr[1];
        joinCondExpr[0] = joinExpr;

        ColumnarFile outerColumnarFile = new ColumnarFile(outerFile);
        in1 = outerColumnarFile.getColumnarHeader().getColumns();

        FldSpec[] outerFldSpecs = new FldSpec[in1.length];

        for (int j = 0; j < in1.length; j++) {
            outerFldSpecs[j] = new FldSpec(new RelSpec(RelSpec.outer), j + 1);
        }

        ColumnarFile innerColumnarFile = new ColumnarFile(innerFile);
        in2 = innerColumnarFile.getColumnarHeader().getColumns();

        IndexType[] indexTypes = new IndexType[1];
        indexTypes[0] = indexType;

        ArrayList<FldSpec> projectionList = new ArrayList<>(proj1);
        projectionList.addAll(proj2);

        Iterator outerScan;

        if (indexType.indexType == IndexType.None || indexType.indexType == IndexType.ColumnScan) {
            outerScan = new TupleScan(outerColumnarFile);
        } else {
            outerScan = new ColumnarIndexScan(outerFile, null, indexTypes, null, in1,
                null, in1.length, in1.length, outerFldSpecs, outerCondExpr);
        }

        NestedLoopJoins nestedLoopJoins = new NestedLoopJoins(in1, null, in2, null, numBuf,
            outerScan, innerFile, joinCondExpr, innerCondExpr, projectionList.toArray(new FldSpec[0]), projectionList.size(), indexTypes);

        Tuple tuple = nestedLoopJoins.getNext();

        while (tuple != null) {
            System.out.println(tuple);

            tuple = nestedLoopJoins.getNext();
        }

        System.out.println("Iteration Complete");


    }


}
