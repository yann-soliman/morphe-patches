import app.morphe.patcher.util.smali.InlineSmaliCompiler;
import com.android.tools.smali.dexlib2.Opcode;
import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class NaolibSmaliTest {
    @Test
    public void actualParkingInjectionAssembles() throws Exception {
        String source = Files.readString(Path.of("src/main/kotlin/io/github/yannsoliman/patches/naolib/NaolibParkingCachePatch.kt"));
        int call = source.indexOf("method.addInstructions(");
        int start = source.indexOf("\"\"\"", call) + 3;
        int end = source.indexOf("\"\"\"", start);
        assertTrue(call >= 0 && start >= 3 && end > start);
        String smali = source.substring(start, end);
        Matcher constants = Pattern.compile("private const val (\\w+) = \"([^\"]+)\"").matcher(source);
        while (constants.find()) {
            smali = smali.replace("$" + constants.group(1), constants.group(2).replace("\\$", "$"));
        }
        var instructions = InlineSmaliCompiler.Companion.compile(smali, "JZ", 12, false);
        assertEquals(3, instructions.size());
        assertEquals(Opcode.SGET_OBJECT, instructions.get(0).getOpcode());
        assertEquals(Opcode.INVOKE_VIRTUAL, instructions.get(1).getOpcode());
        assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions.get(2).getOpcode());
        String malformed = smali.replace("->a:", "->a ");
        assertNotEquals(smali, malformed);
        assertThrows(IllegalStateException.class,
            () -> InlineSmaliCompiler.Companion.compile(malformed, "JZ", 12, false));
    }
}
