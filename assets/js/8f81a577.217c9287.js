"use strict";(self.webpackChunkdocs=self.webpackChunkdocs||[]).push([[16],{5869:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>c,contentTitle:()=>i,default:()=>l,frontMatter:()=>o,metadata:()=>s,toc:()=>d});const s=JSON.parse('{"id":"fundamentals/passing-arguments","title":"Passing Arguments","description":"The instance of the screen object passed to navigate is the same instance passed into the screen content. Define arguments on your screen object:","source":"@site/docs/fundamentals/passing-arguments.md","sourceDirName":"fundamentals","slug":"/fundamentals/passing-arguments","permalink":"/compose-router/fundamentals/passing-arguments","draft":false,"unlisted":false,"tags":[],"version":"current","sidebarPosition":4,"frontMatter":{"sidebar_position":4},"sidebar":"tutorialSidebar","previous":{"title":"Tab Navigation","permalink":"/compose-router/fundamentals/tab-navigation"},"next":{"title":"Animations","permalink":"/compose-router/fundamentals/animations"}}');var a=t(4848),r=t(8453);const o={sidebar_position:4},i="Passing Arguments",c={},d=[];function u(e){const n={code:"code",h1:"h1",header:"header",p:"p",pre:"pre",...(0,r.R)(),...e.components};return(0,a.jsxs)(a.Fragment,{children:[(0,a.jsx)(n.header,{children:(0,a.jsx)(n.h1,{id:"passing-arguments",children:"Passing Arguments"})}),"\n",(0,a.jsxs)(n.p,{children:["The instance of the screen object passed to ",(0,a.jsx)(n.code,{children:"navigate"})," is the same instance passed into the screen content. Define arguments on your screen object:"]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-kotlin",children:'data class UserProfile(val id: Int)\n\nButton(onClick = {\n    navigator.navigate(UserProfile(123))\n}) {\n    Text("Navigate")\n }\n'})}),"\n",(0,a.jsx)(n.p,{children:"And receive the arguments inside the screen content:"}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-kotlin",children:"screen<UserProfile> { navEntry ->\n    UserProfileScreen(navEntry.screen.id)\n}\n"})})]})}function l(e={}){const{wrapper:n}={...(0,r.R)(),...e.components};return n?(0,a.jsx)(n,{...e,children:(0,a.jsx)(u,{...e})}):u(e)}},8453:(e,n,t)=>{t.d(n,{R:()=>o,x:()=>i});var s=t(6540);const a={},r=s.createContext(a);function o(e){const n=s.useContext(r);return s.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function i(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:o(e.components),s.createElement(r.Provider,{value:n},e.children)}}}]);