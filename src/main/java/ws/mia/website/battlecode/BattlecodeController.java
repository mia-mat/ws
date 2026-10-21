package ws.mia.website.battlecode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/// Dashboard for our Battlecode team (Ada, yui, Kara, mia), shown at the root of battlecode.mia.ws
/// (see {@link BattlecodeHostFilter}) and at /battlecode/ on any host, e.g. for staging.
///
/// The server only renders the page shell. dashboard.js fetches the team's public status.json from
/// `battlecode.feed` in the browser, so this app stores nothing and needs no secrets.
@Controller
public class BattlecodeController {

    private final String feed;

    public BattlecodeController(@Value("${battlecode.feed:}") String feed) {
        this.feed = feed.strip();
    }

    @GetMapping({"/battlecode", "/battlecode/"})
    String dashboard(Model model) {
        model.addAttribute("feed", feed);
        return "battlecode";
    }

}
