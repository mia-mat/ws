package ws.mia.website;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WsController {

    @GetMapping
    String root() {
        return "root";
    }

}
