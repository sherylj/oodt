<?php
$activeTab = 'training';
?>
<div class="breadcrumbs">
    <a href="<?php echo SITE_ROOT?>/">Home</a> &nbsp;&rarr;&nbsp;
<a href="<?php echo SITE_ROOT?>/training">Training</a> &nbsp;&rarr;&nbsp;
    Videos

</div>

    <?php echo Puny::container()->load('training.videos');?>