<%@taglib uri="/struts-tags" prefix="s"%>

<ul class="menu">
    <li class="ternary">
        <a href="<s:url action="products_juices" namespace="/"/>" >Juices</a><br>
    </li>
    <li class="ternary">
        <a href="<s:url action="products_accessoires" namespace="/"/>">Accessoires</a><br>
    </li>
    <li class="ternary">
        <a href="<s:url action="products_books" namespace="/"/>" >Books</a><br>
    </li>
    <li class="ternary">
        <a href="<s:url action="products_courses" namespace="/"/>" >Courses</a><br>
    </li>
</ul>
