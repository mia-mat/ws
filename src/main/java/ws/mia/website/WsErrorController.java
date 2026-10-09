package ws.mia.website;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WsErrorController implements ErrorController {

    @RequestMapping("/error")
    public String error() {
        return "redirect:/";
    }
}
