package pro.gravit.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import pro.gravit.launcher.ClientLauncherWrapper;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.ClientModuleManager;
import pro.gravit.launcher.modules.LauncherModulesManager;
import pro.gravit.utils.helper.EnvHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

public class ClientLauncherWrapper {
  public static final String MAGIC_ARG = "-Djdk.attach.allowAttachSelf";
  
  public static final String WAIT_PROCESS_PROPERTY = "launcher.waitProcess";
  
  public static final String NO_JAVA_CHECK_PROPERTY = "launcher.noJavaCheck";
  
  public static boolean noJavaCheck = Boolean.getBoolean("launcher.noJavaCheck");
  
  public static boolean waitProcess = Boolean.getBoolean("launcher.waitProcess");
  
  public static int launcherMemoryLimit = 256;
  
  public static void main(String[] paramArrayOfString) throws IOException, InterruptedException {
    LogHelper.printVersion("Launcher");
    LogHelper.printLicense("Launcher");
    JVMHelper.checkStackTrace(ClientLauncherWrapper.class);
    JVMHelper.verifySystemProperties(Launcher.class, true);
    EnvHelper.checkDangerousParams();
    LauncherConfig launcherConfig = Launcher.getConfig();
    LauncherEngine.modulesManager = new ClientModuleManager();
    LauncherConfig.initModules((LauncherModulesManager)LauncherEngine.modulesManager);
    LogHelper.info("Launcher for project %s", new Object[] { launcherConfig.projectName });
    if (launcherConfig.environment.equals(LauncherConfig.LauncherEnvironment.PROD)) {
      if (System.getProperty("launcher.debug") != null)
        LogHelper.warning("Found -Dlauncher.debug=true"); 
      if (System.getProperty("launcher.stacktrace") != null)
        LogHelper.warning("Found -Dlauncher.stacktrace=true"); 
      LogHelper.info("Debug mode disabled (found env PRODUCTION)");
    } else {
      LogHelper.info("If need debug output use -Dlauncher.debug=true");
      LogHelper.info("If need stacktrace output use -Dlauncher.stacktrace=true");
      if (LogHelper.isDebugEnabled())
        waitProcess = true; 
    } 
    LogHelper.info("Restart Launcher with JavaAgent...");
    ProcessBuilder processBuilder = new ProcessBuilder(new String[0]);
    if (waitProcess)
      processBuilder.inheritIO(); 
    JavaVersion javaVersion = null;
    try {
      if (!noJavaCheck)
        javaVersion = findJava(); 
    } catch (Throwable throwable) {
      LogHelper.error(throwable);
    } 
    if (javaVersion == null)
      javaVersion = JavaVersion.getCurrentJavaVersion(); 
    Path path = IOHelper.resolveJavaBin(javaVersion.jvmDir);
    LinkedList<String> linkedList = new LinkedList();
    linkedList.add(path.toString());
    String str = IOHelper.getCodeSource(LauncherEngine.class).toString();
    linkedList.add(JVMHelper.jvmProperty("launcher.debug", Boolean.toString(LogHelper.isDebugEnabled())));
    linkedList.add(JVMHelper.jvmProperty("launcher.stacktrace", Boolean.toString(LogHelper.isStacktraceEnabled())));
    linkedList.add(JVMHelper.jvmProperty("launcher.dev", Boolean.toString(LogHelper.isDevEnabled())));
    JVMHelper.addSystemPropertyToArgs(linkedList, "launcher.customdir");
    JVMHelper.addSystemPropertyToArgs(linkedList, "launcher.usecustomdir");
    JVMHelper.addSystemPropertyToArgs(linkedList, "launcher.useoptdir");
    JVMHelper.addSystemPropertyToArgs(linkedList, "launcher.dirwatcher.ignoreOverflows");
    if (javaVersion.version >= 9) {
      LogHelper.debug("Found Java 9+ ( %s )", new Object[] { System.getProperty("java.version") });
      String str1 = System.getenv("PATH_TO_FX");
      Path path1 = (str1 == null) ? null : Paths.get(str1, new String[0]);
      StringBuilder stringBuilder = new StringBuilder();
      Path[] arrayOfPath = { javaVersion.jvmDir, javaVersion.jvmDir.resolve("jre"), path1 };
      tryAddModule(arrayOfPath, "javafx.base", stringBuilder);
      tryAddModule(arrayOfPath, "javafx.graphics", stringBuilder);
      tryAddModule(arrayOfPath, "javafx.fxml", stringBuilder);
      tryAddModule(arrayOfPath, "javafx.controls", stringBuilder);
      boolean bool = tryAddModule(arrayOfPath, "javafx.swing", stringBuilder);
      String str2 = stringBuilder.toString();
      if (!str2.isEmpty()) {
        linkedList.add("--add-modules");
        String str3 = "javafx.base,javafx.fxml,javafx.controls,jdk.unsupported";
        if (bool)
          str3 = str3.concat(",javafx.swing"); 
        linkedList.add(str3);
        linkedList.add("--module-path");
        linkedList.add(str2);
      } 
    } 
    linkedList.add("-Djdk.attach.allowAttachSelf");
    linkedList.add("-XX:+DisableAttachMechanism");
    linkedList.add("-Xmx256M");
    linkedList.add("-cp");
    linkedList.add(str);
    linkedList.add(LauncherEngine.class.getName());
    LauncherEngine.modulesManager.callWrapper(processBuilder, linkedList);
    EnvHelper.addEnv(processBuilder);
    LogHelper.debug("Commandline: " + linkedList);
    processBuilder.command(linkedList);
    Process process = processBuilder.start();
    if (!waitProcess) {
      Thread.sleep(3000L);
      if (!process.isAlive()) {
        int i = process.exitValue();
        if (i != 0) {
          LogHelper.error("Process exit with error code: %d", new Object[] { Integer.valueOf(i) });
        } else {
          LogHelper.info("Process exit with code 0");
        } 
      } else {
        LogHelper.debug("Process started success");
      } 
    } else {
      process.waitFor();
    } 
  }
  
  public static Path tryFindModule(Path paramPath, String paramString) {
    Path path = paramPath.resolve(paramString.concat(".jar"));
    LogHelper.dev("Try resolve %s", new Object[] { path.toString() });
    if (!IOHelper.isFile(path)) {
      path = paramPath.resolve("lib").resolve(paramString.concat(".jar"));
    } else {
      return path;
    } 
    return !IOHelper.isFile(path) ? null : path;
  }
  
  public static boolean tryAddModule(Path[] paramArrayOfPath, String paramString, StringBuilder paramStringBuilder) {
    for (Path path : paramArrayOfPath) {
      if (path != null) {
        Path path1 = tryFindModule(path, paramString);
        if (path1 != null) {
          if (paramStringBuilder.length() != 0)
            paramStringBuilder.append(File.pathSeparatorChar); 
          paramStringBuilder.append(path1.toAbsolutePath().toString());
          return true;
        } 
      } 
    } 
    return false;
  }
  
  public static JavaVersion findJavaByProgramFiles(Path paramPath) {
    LogHelper.debug("Check Java in %s", new Object[] { paramPath.toString() });
    JavaVersion javaVersion = null;
    File[] arrayOfFile = paramPath.toFile().listFiles(File::isDirectory);
    if (arrayOfFile == null)
      return null; 
    for (File file : arrayOfFile) {
      Path path = file.toPath();
      try {
        JavaVersion javaVersion1 = JavaVersion.getByPath(path);
        if (javaVersion1 != null && javaVersion1.version >= 8) {
          LogHelper.debug("Found Java %d in %s (javafx %s)", new Object[] { Integer.valueOf(javaVersion1.version), javaVersion1.jvmDir.toString(), javaVersion1.enabledJavaFX ? "true" : "false" });
          if (javaVersion1.enabledJavaFX && (javaVersion == null || !javaVersion.enabledJavaFX)) {
            javaVersion = javaVersion1;
          } else if (javaVersion != null && javaVersion1.enabledJavaFX && javaVersion1.version < javaVersion.version) {
            javaVersion = javaVersion1;
          } 
        } 
      } catch (IOException iOException) {
        LogHelper.error(iOException);
      } 
    } 
    if (javaVersion != null)
      LogHelper.debug("Selected Java %d in %s (javafx %s)", new Object[] { Integer.valueOf(javaVersion.version), javaVersion.jvmDir.toString(), javaVersion.enabledJavaFX ? "true" : "false" }); 
    return javaVersion;
  }
  
  public static JavaVersion findJava() {
    if (JVMHelper.OS_TYPE == JVMHelper.OS.MUSTDIE) {
      JavaVersion javaVersion = null;
      Path path = Paths.get(System.getProperty("java.home"), new String[0]).getParent();
      if (path.getParent().getFileName().toString().contains("x86")) {
        Path path1 = path.getParent().getParent().resolve("Program Files").resolve("Java");
        if (IOHelper.isDir(path1))
          javaVersion = findJavaByProgramFiles(path1); 
      } 
      if (javaVersion == null)
        javaVersion = findJavaByProgramFiles(path); 
      return javaVersion;
    } 
    return null;
  }
  
  public static int getJavaVersion(String paramString) {
    if (paramString.startsWith("1.")) {
      paramString = paramString.substring(2, 3);
    } else {
      int i = paramString.indexOf(".");
      if (i != -1)
        paramString = paramString.substring(0, i); 
    } 
    return Integer.parseInt(paramString);
  }
}

public class JavaVersion {
  public final Path jvmDir;
  
  public final int version;
  
  public boolean enabledJavaFX;
  
  public JavaVersion(Path paramPath, int paramInt) {
    this.jvmDir = paramPath;
    this.version = paramInt;
    this.enabledJavaFX = true;
  }
  
  public static JavaVersion getCurrentJavaVersion() {
    return new JavaVersion(Paths.get(System.getProperty("java.home"), new String[0]), JVMHelper.getVersion());
  }
  
  public static JavaVersion getByPath(Path paramPath) throws IOException {
    Path path = paramPath.resolve("release");
    if (!IOHelper.isFile(path))
      return null; 
    Properties properties = new Properties();
    properties.load(IOHelper.newReader(path));
    int i = ClientLauncherWrapper.getJavaVersion(properties.getProperty("JAVA_VERSION").replaceAll("\"", ""));
    JavaVersion javaVersion = new JavaVersion(paramPath, i);
    if (i <= 8) {
      javaVersion.enabledJavaFX = isExistExtJavaLibrary(paramPath, "jfxrt");
    } else {
      javaVersion.enabledJavaFX = (ClientLauncherWrapper.tryFindModule(paramPath, "javafx.base") != null);
      if (!javaVersion.enabledJavaFX)
        javaVersion.enabledJavaFX = (ClientLauncherWrapper.tryFindModule(paramPath.resolve("jre"), "javafx.base") != null); 
    } 
    return javaVersion;
  }
  
  public static boolean isExistExtJavaLibrary(Path paramPath, String paramString) {
    Path path1 = paramPath.resolve("lib").resolve("ext").resolve(paramString.concat(".jar"));
    Path path2 = paramPath.resolve("jre").resolve("lib").resolve("ext").resolve(paramString.concat(".jar"));
    return (IOHelper.isFile(path1) || IOHelper.isFile(path2));
  }
}
