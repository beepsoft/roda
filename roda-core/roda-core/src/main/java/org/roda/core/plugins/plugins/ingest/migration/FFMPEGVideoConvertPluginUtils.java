package org.roda.core.plugins.plugins.ingest.migration;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.roda.core.RodaCoreFactory;
import org.roda.core.util.CommandException;
import org.roda.core.util.CommandUtility;

public class FFMPEGVideoConvertPluginUtils {

  public static Path runFFMPEGVideoConvert(InputStream is, String inputFormat, String outputFormat) throws IOException,
    CommandException {

    // write the inputstream data on a new file (absolute path needed)
    Path input = Files.createTempFile("copy", "." + inputFormat);
    byte[] buffer = new byte[is.available()];
    is.read(buffer);
    OutputStream os = new FileOutputStream(input.toFile());
    os.write(buffer);
    os.close();
    is.close();

    Path output = Files.createTempFile("result", "." + outputFormat);

    return executeFFMPEG(input, output);
  }

  public static Path runFFMPEGVideoConvert(Path input, String inputFormat, String outputFormat) throws IOException,
    CommandException {

    Path output = Files.createTempFile("result", "." + outputFormat);

    return executeFFMPEG(input, output);
  }

  private static Path executeFFMPEG(Path input, Path output) throws CommandException {

    // FIXME replace error
    String command = RodaCoreFactory.getRodaConfigurationAsString("tools", "ffmpegvideoconvert", "commandLine");
    command = command.replace("{input_file}", input.toString());
    command = command.replace("{output_file}", output.toString());
    command = command.replace("\"", "");

    // filling a list of the command line arguments
    List<String> commandList = Arrays.asList(command.split(" "));

    // running the command
    CommandUtility.execute(commandList);
    return output;
  }
}