package cmdline;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Vector;

public class Query {
  private static String columnDBName;
  private static String columnarFileName;
  private static Vector<String> targetColumnNames;
  private static String valueConstraint;
  private static String numBuf;
  private static String accessType;

  public static void main(String argv[]) {
    initFromArgs(argv);
  }

  private static void initFromArgs(String argv[]) {
    throw new NotImplementedException();
  }
}
