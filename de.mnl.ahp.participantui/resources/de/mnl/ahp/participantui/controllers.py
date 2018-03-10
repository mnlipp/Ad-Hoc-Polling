"""
..
   This file is part of the CAdHocPoll program.
   Copyright (C) 2015 Michael N. Lipp
   
   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.
   
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.

.. codeauthor:: mnl
"""

from circuits_minpor.utils.misc import BaseControllerExt
from circuits.web.controllers import expose
from os.path import dirname, join
import rbtranslations
from circuits_bricks.web.misc import LanguagePreferences
from cadhocpoll.model.pollmanager import poll_result

class DialogState(object):
    START = 0
    AUTHORIZED = 1
    VOTED = 2

class VotingController(BaseControllerExt):
    
    def __init__(self, *args, **kwargs):
        super(VotingController, self).__init__(*args, **kwargs)
        self.engine.path = [dirname(__file__)]
    
    def _dialog_state(self):
        return self.session.get("dialog_state", DialogState.START)
    
    @expose("index")
    def index(self, *args, **kwargs):
        dialog_state = self._dialog_state()
        
        if "set_locale" in kwargs:
            LanguagePreferences.override_accept \
                (self.session, kwargs.get("set_locale", "en"), self.response)
        
        if dialog_state == DialogState.START and "submit_code" in kwargs:
            self._set_code(kwargs.get("code"))

        if dialog_state == DialogState.AUTHORIZED and "chosen" in kwargs:
            self._set_chosen(kwargs.get("chosen"))
            return self.redirect(self.channel)

        response = self._render_response()
        if self._dialog_state() == DialogState.VOTED:
            self.session["dialog_state"] = DialogState.START
        return response


    def _set_code(self, code):
        try:
            self.session["selected_poll"] = int(code)
            self.session["dialog_state"] = DialogState.AUTHORIZED
        except:
            # if conversion to integer fails
            pass
        pass

    def _set_chosen(self, chosen):
        try:
            chosen = int(chosen)
            poll = self.session["selected_poll"]
            self.fire(poll_result(poll, chosen), "result")
            del self.session["selected_poll"]
            self.session["dialog_state"] = DialogState.VOTED
        except:
            # if conversion to integer fails
            pass

    def _render_response(self):
        translation = rbtranslations.translation \
            ("l10n", __file__, 
             LanguagePreferences.preferred(self.session), "en")
        page_selector = {
            DialogState.START: "auth_page.pyhtml",
            DialogState.AUTHORIZED: "vote_page.pyhtml",
            DialogState.VOTED: "bye_page.pyhtml",
        }
        page = page_selector.get(self._dialog_state(), "auth_page.pyhtml")
        path = join(dirname(__file__), page)
        return self.serve_tenjin \
            (self.request, self.response, path, {}, \
             globexts = { "_": translation.ugettext,
                          "page_path": self.channel \
                                        if self.channel != "/" else "" })

    
